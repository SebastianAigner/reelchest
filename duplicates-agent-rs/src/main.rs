use std::collections::{HashMap};
use std::time::Instant;
use async_channel::{Receiver, Sender};
use itertools::Itertools;
use rand::rngs::ThreadRng;
use rand::seq::SliceRandom;
use rand::thread_rng;
use serde::{Deserialize, Serialize};
use tokio::task::JoinHandle;

type Foo = u64;

#[tokio::main]
async fn main() {
    let (jobs_sender_channel, jobs_receiver_channel) = async_channel::unbounded();
    let (res_sender_channel, res_receiver_channel) = async_channel::unbounded();
    get_all_hashes().await;
    let hashes = get_all_hashes().await;
    let start = Instant::now();
    let my_hashes_clone = hashes.clone();
    for (id, curr_hashes) in my_hashes_clone {
        match jobs_sender_channel.send((id, curr_hashes)).await {
            Ok(_) => {}
            Err(x) => {
                println!("{}", x)
            }
        }
    }
    jobs_sender_channel.close();
    let handles: Vec<_> = (0..20).map(|i| {
        spawn_worker(jobs_receiver_channel.clone(), hashes.clone(), res_sender_channel.clone())
    }).collect();
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
                println!("err: {}", e);
                break;
            }
        }
    }
    final_vec.sort_by(|a, b| {
        a.distance.cmp(&b.distance)
    });
    let candidates: Vec<_> = final_vec.iter().take(10).collect();
    let outstr = candidates
        .iter()
        .map(|x| {
            format!("{} -{}-> {}", x.a, x.distance, x.b)
        })
        .join("\n");
    println!("{}", outstr);
    let done = start.elapsed();
    println!("Done in {:?}", done);
}

#[derive(Debug)]
struct Duplicate {
    a: String,
    distance: u32,
    b: String,
}

fn spawn_worker(r: Receiver<(String, Vec<DHash>)>, hashes: HashMap<String, Vec<DHash>>, res_sender_channel: Sender<Duplicate>) -> JoinHandle<()> {
    tokio::spawn(async move {
        loop {
            match r.recv().await {
                Ok((id, curr_hashes)) => {
                    let res = calculate_duplicate(&id, &curr_hashes, &hashes);
                    println!("{} {:?}", id, res);
                    match res {
                        None => {}
                        Some(other) => {
                            res_sender_channel.send(Duplicate {
                                a: id,
                                b: other.id,
                                distance: other.distance,
                            }).await.expect("Really would like to be able to send my dude");
                        }
                    }
                }
                Err(_) => { return; }
            }
        }
    })
}


#[derive(Debug)]
struct IdWithDistance {
    id: String,
    distance: u32,
}


fn calculate_duplicate(current_id: &String, current_hashes: &Vec<DHash>, all_hashes: &HashMap<String, Vec<DHash>>) -> Option<IdWithDistance> {
    let mut rng: ThreadRng = thread_rng();
    if current_hashes.is_empty() {
        return None;
    } else {
        let handful: Vec<_> = current_hashes.choose_multiple(&mut rng, 100).collect();
        let all_other_hashes = all_hashes
            .into_iter()
            .filter(|(id, _)| {
                id != &current_id
            });
        let minimal_deviations_from_this_hash: HashMap<_, _> = all_other_hashes
            .filter(|(_, hashes)| {
                !hashes.is_empty()
            })
            .map(|(other_hash_id, other_hash)| {
                let devation_from_this_hash: u32 = handful
                    .iter()
                    .map(|sample| {
                        minimal_distance(other_hash, &sample)
                    })
                    .sum();
                (other_hash_id, devation_from_this_hash)
            })
            .collect();

        let (id_with_smallest_deviation, deviation) = minimal_deviations_from_this_hash
            .into_iter()
            .min_by(|(_, a_deviation), (_, b_deviation)| {
                a_deviation.cmp(b_deviation)
            })
            .expect("Couldn't smallest deviation!");

        Some(IdWithDistance {
            id: id_with_smallest_deviation.clone(),
            distance: deviation,
        })
    }
}


async fn get_all_hashes() -> HashMap<String, Vec<DHash>> {
    let entries: Vec<MediaLibraryEntry> = reqwest::get(endpoint).await.unwrap().json().await.unwrap();

    let ids: Vec<_> = entries
        .into_iter()
        .map(|entry| entry.uid)
        //.take(100) // TODO: Unlimit this
        .collect();

    let hashes = ids
        .iter()
        .map(|id| {
            get_hashes_for_id(&id)
        });

    let results = futures::future::join_all(hashes).await;

    let ids_to_hashes: HashMap<_, _> = ids
        .into_iter() // interesting! that's so that we can move the string into
        .zip(results)
        .collect();

    ids_to_hashes
}

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct MediaLibraryEntry {
    uid: String,
}

#[derive(Debug, Clone)]
struct DHash {
    raw: u64,
}

impl DHash {
    fn distance_to(&self, other: &Self) -> u32 {
        let xorred = self.raw ^ other.raw;
        xorred.count_ones()
    }
}

fn minimal_distance(hashes: &[DHash], target: &DHash) -> u32 {
    hashes
        .iter()
        .map(|a| {
            a.distance_to(target)
        })
        .min()
        .unwrap_or_else(|| {
            let problem = format!("Couldn't compute minimal distance. Probably empty hashes? {:?} {:?}", hashes, target);
            panic!("{}", problem);
        })
}

const endpoint: &str = "http://192.168.178.165:8080/api/mediaLibrary";

async fn get_hashes_for_id(id: &str) -> Vec<DHash> {
    println!("Getting hashes for {}", id);
    let client = reqwest::Client::new();

    let url = format!("{}/{}/hash.bin", endpoint, id);
    let res = client.get(&url).send().await;

    match res {
        Ok(response) => {
            if response.status() == reqwest::StatusCode::NOT_FOUND {
                vec![]
            } else {
                let bytes = response.bytes().await.expect("Didn't get bytes from URL!");
                let size = core::mem::size_of::<u64>();
                let chunks = bytes.chunks_exact(size);
                let bytes = chunks.map(|chunk| {
                    DHash { raw: u64::from_be_bytes(chunk.try_into().expect("Couldn't turn into longs!")) }
                }).collect_vec();
                bytes
            }
        }
        Err(_) => vec![],
    }
}