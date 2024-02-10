use std::collections::HashMap;
use std::convert::Into;
use std::fs::File;
use std::io::{BufReader, BufWriter, Write};
use std::sync::Arc;
use std::time::Instant;

use async_channel::{Receiver, Sender};
use itertools::FoldWhile::{Continue, Done};
use itertools::Itertools;
use rand::rngs::ThreadRng;
use rand::seq::SliceRandom;
use rand::thread_rng;
use serde::{Deserialize, Serialize};
use tokio::task::JoinHandle;

// -----
// https://users.rust-lang.org/t/satisfying-tokio-spawn-static-lifetime-requirement/78773/8
#[derive(Clone)]
struct Runner {
    shared: Arc<RunnerInner>,
}

#[derive(Clone)]
struct RunnerInner {
    recv_chan: Receiver<(Arc<str>, Vec<DHash>)>,
    sender_chan: Sender<Duplicate>,
    hashes: HashMap<Arc<str>, Vec<DHash>>,
}
// -----

fn write_hashes_to_disk(hashes: &HashMap<String, Vec<DHash>>) -> Result<(), std::io::Error> {
    let file = File::create("hashes.json")?;
    let mut writer = BufWriter::new(file);
    serde_json::to_writer(&mut writer, &hashes)?;
    writer.flush()?;
    Ok(())
}

fn load_hashes_from_disk() -> Option<HashMap<String, Vec<DHash>>> {
    let file = File::open("hashes.json").ok()?;
    let reader = BufReader::new(file);
    println!("Reading from disk...");
    serde_json::from_reader(reader).ok()?
}

#[tokio::main]
async fn main() {
    let (jobs_sender_channel, jobs_receiver_channel) = async_channel::unbounded();
    let (res_sender_channel, res_receiver_channel) = async_channel::unbounded();
    let disk_hashes = load_hashes_from_disk();
    let hashes = if let Some(read_hashes) = disk_hashes {
        read_hashes
    } else {
        let network_hashes = get_all_hashes().await;
        println!("Writing to disk...");
        write_hashes_to_disk(&network_hashes).unwrap();
        network_hashes
    };

    let hashes: HashMap<Arc<str>, Vec<DHash>> = hashes.clone().iter().map(|(k, v)| {
        let key: Arc<str> = k.clone().into_boxed_str().into();
        (key, v.clone())
    }).collect();

    let start = Instant::now();
    let my_hashes_clone = hashes.clone();

    for (id, curr_hashes) in my_hashes_clone {
        if let Err(x) = jobs_sender_channel.send((id, curr_hashes)).await {
            println!("{x}");
        }
    }

    jobs_sender_channel.close();

    let runner = Runner {
        shared: Arc::new(RunnerInner {
            recv_chan: jobs_receiver_channel.clone(),
            sender_chan: res_sender_channel.clone(),
            hashes,
        }),
    };

    let handles: Vec<_> = (0..20)
        .map(|_| spawn_worker(runner.shared.clone()))
        .collect();
    for handle in handles {
        handle.await.expect("TODO: panic message");
    }
    res_receiver_channel.close(); // todo: it seems annoying that i have to do this "manually" after everything is done.
    // but if i don't, the loop below will hang.

    let mut final_vec = vec![];

    loop {
        match res_receiver_channel.recv().await {
            Ok(a) => {
                final_vec.push(a);
            }
            Err(e) => {
                println!("err: {e}");
                break;
            }
        }
    }
    final_vec.sort_by(|a, b| a.distance.cmp(&b.distance));
    // let candidates: Vec<_> = final_vec.iter().take(10).collect();
    let out_string = final_vec
        .iter()
        .filter(|x| { x.distance == 0 })
        .map(|x| format!("{} -{}-> {}", x.a, x.distance, x.b))
        .join("\n");
    println!("{out_string}");
    let done = start.elapsed();
    println!("Done in {done:?}");
}

#[derive(Debug)]
struct Duplicate {
    a: Arc<str>,
    distance: u32,
    b: Arc<str>,
}

fn spawn_worker(shared: Arc<RunnerInner>) -> JoinHandle<()> {
    tokio::spawn(async move {
        loop {
            if let Ok((id, curr_hashes)) = shared.recv_chan.recv().await {
                let res = calculate_duplicate_fast_fold(&id, &curr_hashes, &shared.hashes);
                println!("{id} {res:?}");

                if let Some(other) = res {
                    shared
                        .sender_chan
                        .send(Duplicate {
                            a: id,
                            b: other.id,
                            distance: other.distance,
                        })
                        .await
                        .expect("Really would like to be able to send my dude");
                }
            } else {
                return;
            }
        }
    })
}

#[derive(Debug)]
struct IdWithDistance {
    id: Arc<str>,
    distance: u32,
}

// 52s on test dataset.
fn calculate_duplicate(
    current_id: &Arc<str>,
    current_hashes: &Vec<DHash>,
    all_hashes: &HashMap<Arc<str>, Vec<DHash>>,
) -> Option<IdWithDistance> {
    let mut rng: ThreadRng = thread_rng();
    if current_hashes.is_empty() {
        return None;
    }

    let handful: Vec<_> = current_hashes.choose_multiple(&mut rng, 100).collect();
    let all_other_hashes = all_hashes.iter().filter(|(id, _)| id != &current_id);
    let all_nonempty_ids = all_other_hashes
        .filter(|(_, hashes)| !hashes.is_empty());
    let minimal_deviations_from_this_hash = all_nonempty_ids
        .map(|(other_hash_id, other_hash)| {
            let devation_from_this_hash: u32 =
                handful
                    .iter()
                    .map(|&&sample| {
                        minimal_distance(other_hash, sample)
                    })
                    .sum();
            (other_hash_id, devation_from_this_hash)
        });

    let (id_with_smallest_deviation, deviation) = minimal_deviations_from_this_hash
        .into_iter()
        .min_by(|(_, a_deviation), (_, b_deviation)| a_deviation.cmp(b_deviation))
        .expect("Couldn't smallest deviation!");

    Some(IdWithDistance {
        id: id_with_smallest_deviation.clone(),
        distance: deviation,
    })
}

/// Fast implementation of calculate_duplicate.
/// Where calculate_duplicate evaluates all distances before determining the minimum,
/// calculate_duplicate_fast stops its computations for a given hash when it's clear
/// that the intermediate computation result already disqualifies it as the shortest distance,
/// and moves on directly to the next group to be tested.
/// 40s on test dataset
fn calculate_duplicate_fast(
    current_id: &Arc<str>,
    current_hashes: &Vec<DHash>,
    all_hashes: &HashMap<Arc<str>, Vec<DHash>>,
) -> Option<IdWithDistance> {
    let mut rng: ThreadRng = thread_rng();
    if current_hashes.is_empty() {
        return None;
    }

    let handful_hashgroup: Vec<_> = current_hashes.choose_multiple(&mut rng, 100).collect();
    let all_other_hashes = all_hashes.iter().filter(|(id, group)| id != &current_id && !group.is_empty());
    let mut current_minimum_distance = u32::MAX;
    let mut current_minimum_entry_id: Arc<str> = "none".into();

    // we take a handful of hashes
    // for those 100 hashes, look at other-hashgroup.
    // look at each handful-entry. find the CLOSEST match in the other-hashgroup. sum up those closest match distances.
    // the accumulation of those is the total minimal distance between the handful and other-hashgroup.
    for (other_entry_id, other_hashgroup) in all_other_hashes {
        let mut distance_accumulator = 0;
        for this_dhash in &handful_hashgroup {
            if distance_accumulator >= current_minimum_distance {
                break;
            }
            let minimal_distance = minimal_distance_to_group(**this_dhash, other_hashgroup);
            distance_accumulator += minimal_distance;
        }
        if distance_accumulator < current_minimum_distance {
            // println!("found closer distance, going from {} to {}", current_minimum_distance, cum_dist);
            current_minimum_distance = distance_accumulator;
            current_minimum_entry_id = other_entry_id.clone();
        }
    }
    return Some(IdWithDistance {
        id: current_minimum_entry_id,
        distance: current_minimum_distance,
    });
}

/// Same as calculate_duplicate_fast, but uses fold_while (an early-terminating fold) instead of just loops.
fn calculate_duplicate_fast_fold(
    current_id: &Arc<str>,
    current_hashes: &Vec<DHash>,
    all_hashes: &HashMap<Arc<str>, Vec<DHash>>,
) -> Option<IdWithDistance> {
    let mut rng: ThreadRng = thread_rng();
    if current_hashes.is_empty() {
        return None;
    }

    let handful_hashgroup: Vec<_> = current_hashes.choose_multiple(&mut rng, 100).collect();
    let all_other_hashes = all_hashes.iter().filter(|(id, group)| id != &current_id && !group.is_empty());
    let mut current_minimum_distance = u32::MAX;
    let mut current_minimum_entry_id: Arc<str> = "none".into();

    // we take a handful of hashes
    // for those 100 hashes, look at other-hashgroup.
    // look at each handful-entry. find the CLOSEST match in the other-hashgroup. sum up those closest match distances.
    // the accumulation of those is the total minimal distance between the handful and other-hashgroup.
    for (other_entry_id, other_hashgroup) in all_other_hashes {
        let distance_accumulator = handful_hashgroup.iter().fold_while(0, |curr_dist_acc, this_dhash| {
            if curr_dist_acc > current_minimum_distance {
                Done(curr_dist_acc)
            } else {
                let new_acc = curr_dist_acc + minimal_distance_to_group(**this_dhash, other_hashgroup);
                Continue(new_acc)
            }
        }).into_inner();
        if distance_accumulator < current_minimum_distance {
            // println!("found closer distance, going from {} to {}", current_minimum_distance, cum_dist);
            current_minimum_distance = distance_accumulator;
            current_minimum_entry_id = other_entry_id.clone();
        }
    }
    return Some(IdWithDistance {
        id: current_minimum_entry_id,
        distance: current_minimum_distance,
    });
}

async fn get_all_hashes() -> HashMap<String, Vec<DHash>> {
    let entries: Vec<MediaLibraryEntry> =
        reqwest::get(ENDPOINT).await.unwrap().json().await.unwrap();

    let ids: Vec<_> = entries
        .into_iter()
        .map(|entry| entry.uid)
        //.take(100) // TODO: Unlimit this
        .collect();

    let hashes = ids.iter().map(|id| get_hashes_for_id(id));

    let results = futures::future::join_all(hashes).await;

    ids.into_iter() // interesting! that's so that we can move the string into
        .zip(results)
        .collect()
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct MediaLibraryEntry {
    uid: String,
}

#[derive(Debug, Copy, Clone, Serialize, Deserialize)]
struct DHash {
    raw: u64,
}

impl DHash {
    const fn distance_to(self, other: Self) -> u32 {
        let xorred = self.raw ^ other.raw;
        xorred.count_ones()
    }
}

fn minimal_distance_to_group(from: DHash, to_hashes: &[DHash]) -> u32 {
    minimal_distance(to_hashes, from)
}

fn minimal_distance(hashes: &[DHash], target: DHash) -> u32 {
    hashes
        .iter()
        .map(|a| a.distance_to(target))
        .min()
        .unwrap_or_else(|| {
            let problem = format!(
                "Couldn't compute minimal distance. Probably empty hashes? {hashes:?} {target:?}",
            );
            panic!("{}", problem);
        })
}

const ENDPOINT: &str = "http://192.168.178.165:8080/api/mediaLibrary";

async fn get_hashes_for_id(id: &str) -> Vec<DHash> {
    println!("Getting hashes for {id}");
    let client = reqwest::Client::new();

    let url = format!("{ENDPOINT}/{id}/hash.bin");
    let res = client.get(&url).send().await;

    match res {
        Ok(response) => {
            if response.status() == reqwest::StatusCode::NOT_FOUND {
                vec![]
            } else {
                let bytes = response.bytes().await.expect("Didn't get bytes from URL!");
                let size = core::mem::size_of::<u64>();
                let chunks = bytes.chunks_exact(size);
                chunks
                    .map(|chunk| DHash {
                        raw: u64::from_be_bytes(
                            chunk.try_into().expect("Couldn't turn into longs!"),
                        ),
                    })
                    .collect()
            }
        }
        Err(_) => vec![],
    }
}
