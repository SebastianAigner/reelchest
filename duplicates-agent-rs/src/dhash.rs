use serde::{Deserialize, Serialize};

#[derive(Debug, Copy, Clone, Serialize, Deserialize)]
pub struct DHash {
    pub raw: u64,
}

impl DHash {
    pub const fn distance_to(self, other: Self) -> u32 {
        let xorred = self.raw ^ other.raw;
        xorred.count_ones()
    }
}