use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use crate::DHash;

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

#[derive(Serialize, Deserialize, Debug)]
#[serde(rename_all = "camelCase")]
struct MediaLibraryEntry {
    uid: String,
}

pub(crate) async fn get_all_hashes() -> HashMap<String, Vec<DHash>> {
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