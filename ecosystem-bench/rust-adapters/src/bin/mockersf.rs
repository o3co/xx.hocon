//! mockersf/hocon.rs adapter — parse a HOCON file, emit resolved tree as JSON.
use std::process::exit;
use mockersf_hocon::{Hocon, HoconLoader};

fn to_json(h: &Hocon) -> serde_json::Value {
    use serde_json::Value;
    match h {
        Hocon::Real(f) => serde_json::json!(f),
        Hocon::Integer(i) => serde_json::json!(i),
        Hocon::String(s) => Value::String(s.clone()),
        Hocon::Boolean(b) => Value::Bool(*b),
        Hocon::Null => Value::Null,
        Hocon::BadValue(_) => Value::Null,
        Hocon::Array(a) => Value::Array(a.iter().map(to_json).collect()),
        Hocon::Hash(m) => {
            let mut o = serde_json::Map::new();
            for (k, v) in m {
                o.insert(k.clone(), to_json(v));
            }
            Value::Object(o)
        }
    }
}

fn main() {
    let args: Vec<String> = std::env::args().collect();
    if args.len() != 2 {
        eprintln!("usage: mockersf <conf-file>");
        exit(2);
    }
    match HoconLoader::new().load_file(&args[1]).and_then(|l| l.hocon()) {
        Ok(h) => println!("{}", serde_json::to_string(&to_json(&h)).unwrap()),
        Err(e) => {
            eprintln!("ERROR: {e:?}");
            exit(1);
        }
    }
}
