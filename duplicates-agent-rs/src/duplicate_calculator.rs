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
    let all_nonempty_ids = all_other_hashes.filter(|(_, hashes)| !hashes.is_empty());
    let minimal_deviations_from_this_hash = all_nonempty_ids.map(|(other_hash_id, other_hash)| {
        let devation_from_this_hash: u32 = handful
            .iter()
            .map(|&&sample| minimal_distance(other_hash, sample))
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
    let all_other_hashes = all_hashes
        .iter()
        .filter(|(id, group)| id != &current_id && !group.is_empty());
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
    let all_other_hashes = all_hashes
        .iter()
        .filter(|(id, group)| id != &current_id && !group.is_empty());
    let mut current_minimum_distance = u32::MAX;
    let mut current_minimum_entry_id: Arc<str> = "none".into();

    // we take a handful of hashes
    // for those 100 hashes, look at other-hashgroup.
    // look at each handful-entry. find the CLOSEST match in the other-hashgroup. sum up those closest match distances.
    // the accumulation of those is the total minimal distance between the handful and other-hashgroup.
    for (other_entry_id, other_hashgroup) in all_other_hashes {
        let distance_accumulator = handful_hashgroup
            .iter()
            .fold_while(0, |curr_dist_acc, this_dhash| {
                if curr_dist_acc > current_minimum_distance {
                    Done(curr_dist_acc)
                } else {
                    let new_acc =
                        curr_dist_acc + minimal_distance_to_group(**this_dhash, other_hashgroup);
                    Continue(new_acc)
                }
            })
            .into_inner();
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

// Autogenerated tests, tread carefully :)

#[cfg(test)]
mod tests {
    use super::*;

    fn create_dhash(value: u64) -> DHash {
        DHash { raw: value }
    }

    #[test]
    fn test_minimal_distance() {
        let hash1 = create_dhash(0b0000); // 0000
        let hash2 = create_dhash(0b0001); // 0001 - distance 1 from hash1
        let hash3 = create_dhash(0b0011); // 0011 - distance 2 from hash1
        let hash4 = create_dhash(0b0111); // 0111 - distance 3 from hash1

        let hashes = vec![hash2, hash3, hash4];

        // Should find hash2 as closest to hash1 with distance 1
        assert_eq!(minimal_distance(&hashes, hash1), 1);

        // Should find hash4 as closest to hash4 with distance 0
        assert_eq!(minimal_distance(&hashes, hash4), 0);
    }

    #[test]
    #[should_panic(expected = "Couldn't compute minimal distance")]
    fn test_minimal_distance_empty_hashes() {
        let empty_hashes: Vec<DHash> = vec![];
        let target = create_dhash(0);
        minimal_distance(&empty_hashes, target);
    }

    #[test]
    fn test_minimal_distance_to_group() {
        let target = create_dhash(0b0000);
        let group = vec![
            create_dhash(0b0001), // distance 1
            create_dhash(0b0011), // distance 2
            create_dhash(0b0111), // distance 3
        ];

        assert_eq!(minimal_distance_to_group(target, &group), 1);
    }

    #[test]
    fn test_calculate_duplicate_fast_fold_empty_input() {
        let current_id: Arc<str> = "test1".into();
        let current_hashes: Vec<DHash> = vec![];
        let all_hashes: HashMap<Arc<str>, Vec<DHash>> = HashMap::new();

        let result = calculate_duplicate_fast_fold(&current_id, &current_hashes, &all_hashes);
        assert!(result.is_none());
    }

    #[test]
    fn test_calculate_duplicate_fast_fold_basic() {
        let id1: Arc<str> = "test1".into();
        let id2: Arc<str> = "test2".into();
        let id3: Arc<str> = "test3".into();

        // Create similar hashes for id1 and id2
        let hash1 = create_dhash(0b0000);
        let hash2 = create_dhash(0b0001); // Close to hash1
        let hash3 = create_dhash(0b1111); // Different from both

        let mut all_hashes: HashMap<Arc<str>, Vec<DHash>> = HashMap::new();
        all_hashes.insert(id1.clone(), vec![hash1; 100]); // Use 100 to match the sample size
        all_hashes.insert(id2.clone(), vec![hash2; 100]);
        all_hashes.insert(id3.clone(), vec![hash3; 100]);

        // Test finding duplicate for id1
        let result = calculate_duplicate_fast_fold(&id1, &all_hashes[&id1], &all_hashes)
            .expect("Should find a duplicate");

        assert_eq!(result.id, id2);
        assert_eq!(result.distance, 100); // Each hash has distance 1, multiplied by 100 samples
    }

    #[test]
    fn test_calculate_duplicate_fast_fold_no_duplicates() {
        let id1: Arc<str> = "test1".into();
        let id2: Arc<str> = "test2".into();

        // Create very different hashes
        let hash1 = create_dhash(0b0000_0000);
        let hash2 = create_dhash(0b1111_1111);

        let mut all_hashes: HashMap<Arc<str>, Vec<DHash>> = HashMap::new();
        all_hashes.insert(id1.clone(), vec![hash1; 100]);
        all_hashes.insert(id2.clone(), vec![hash2; 100]);

        let result = calculate_duplicate_fast_fold(&id1, &all_hashes[&id1], &all_hashes)
            .expect("Should return Some even if distances are large");

        assert_eq!(result.id, id2);
        assert!(result.distance > 0); // Distance should be large as hashes are very different
    }
}
