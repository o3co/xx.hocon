//! mikai233/hocon-rs adapter — parse a HOCON file, emit resolved tree as JSON.
//! hocon-rs is serde-native: parse directly into serde_json::Value.
use std::process::exit;
use mikai_hocon::Config;

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 2 {
        eprintln!("usage: mikai <conf-file>");
        exit(2);
    }
    let src = match std::fs::read_to_string(&args[1]) {
        Ok(s) => s,
        Err(e) => { eprintln!("ERROR: {e}"); exit(1); }
    };
    match Config::parse_str::<serde_json::Value>(&src, None) {
        Ok(v) => println!("{}", serde_json::to_string(&v).unwrap()),
        Err(e) => { eprintln!("ERROR: {e:?}"); exit(1); }
    }
}
