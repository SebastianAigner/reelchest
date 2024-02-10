use std::collections::HashMap;
use std::convert::Into;
use std::sync::Arc;
use std::time::Instant;

use async_channel::{Receiver, Sender};
use itertools::Itertools;
use tokio::task::JoinHandle;

use duplicate_calculator::calculate_duplicate_fast_fold;

use crate::dhash::DHash;
use crate::duplicate_calculator::Duplicate;
use crate::network::get_all_hashes;

mod disk_cache;
mod network;
mod duplicate_calculator;
mod dhash;

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

#[tokio::main]
async fn main() {
    let (jobs_sender_channel, jobs_receiver_channel) = async_channel::unbounded();
    let (res_sender_channel, res_receiver_channel) = async_channel::unbounded();
    let disk_hashes = disk_cache::load_hashes_from_disk();
    let hashes = if let Some(read_hashes) = disk_hashes {
        read_hashes
    } else {
        let network_hashes = get_all_hashes().await;
        println!("Writing to disk...");
        disk_cache::write_hashes_to_disk(&network_hashes).unwrap();
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