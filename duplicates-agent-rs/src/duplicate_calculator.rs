use std::collections::HashMap;
use std::sync::Arc;

use itertools::FoldWhile::{Continue, Done};
use itertools::Itertools;
use rand::prelude::{SliceRandom, ThreadRng};
use rand::thread_rng;

use crate::DHash;

#[derive(Debug)]
pub struct Duplicate {
    pub a: Arc<str>,
    pub distance: u32,
    pub b: Arc<str>,
}

#[derive(Debug)]
pub struct IdWithDistance {
    pub id: Arc<str>,
    pub distance: u32,
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
pub(crate) fn calculate_duplicate_fast_fold(
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

