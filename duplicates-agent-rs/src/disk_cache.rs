use std::collections::HashMap;
use std::fs::File;
use std::io::{BufReader, BufWriter, Write};
use crate::DHash;

pub(crate) fn write_hashes_to_disk(hashes: &HashMap<String, Vec<DHash>>) -> Result<(), std::io::Error> {
    let file = File::create("hashes.json")?;
    let mut writer = BufWriter::new(file);
    serde_json::to_writer(&mut writer, &hashes)?;
    writer.flush()?;
    Ok(())
}

pub(crate) fn load_hashes_from_disk() -> Option<HashMap<String, Vec<DHash>>> {
    let file = File::open("hashes.json").ok()?;
    let reader = BufReader::new(file);
    println!("Reading from disk...");
    serde_json::from_reader(reader).ok()?
}