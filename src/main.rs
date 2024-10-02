use std::{
    fs::File,
    io::{BufRead, BufReader},
    path::PathBuf,
};

use clap::Parser;
use polars::{
    df,
    prelude::{all, col, lit, DataType, IntoLazy, LazyFrame, NamedFromOwned},
    series::Series,
};

#[derive(Parser)]
#[command(version, about, long_about = None)]
struct Args {
    /// Directory of m3u8 files. Note that these m3u8 files must exist and will be
    /// checked for existence: {All,Songs - {CHN,ENG,JAP,KOR,Others}}.m3u8
    #[arg(short = 'd', long)]
    m3u8_dir: PathBuf,
}

static M3U8_NAMES: [&str; 6] = [
    "All",
    "Songs - CN",
    "Songs - EN",
    "Songs - JP",
    "Songs - KR",
    "Songs - O",
];

fn add_language_membership_column(
    df: LazyFrame,
    m3u8_language_path: PathBuf,
    new_column_name: &str,
) -> anyhow::Result<LazyFrame> {
    #[allow(clippy::result_filter_map)]
    let mut m3u8_language_lines = BufReader::new(File::open(&m3u8_language_path)?)
        .lines()
        // .flatten()
        .filter(Result::is_ok)
        .map(Result::unwrap) // would have used .flatten() but that has the danger of `flatten()` will run forever if the iterator repeatedly produces an `Err`
        .skip(1) // skip header line,
        .peekable();

    let mut language_line_file_names = Vec::new();
    while m3u8_language_lines.peek().is_some() {
        m3u8_language_lines.next(); // rating line, ignore
        let line_music_file = m3u8_language_lines
            .next()
            .ok_or_else(|| anyhow::anyhow!(
                "{} is malformed: a rating line is not followed by a line describing the corresponding music file.",
                m3u8_language_path.to_string_lossy()
            ))?;
        language_line_file_names.push(line_music_file)
    }
    Ok(df.with_column(
        col("line_file_name")
            .is_in(lit(Series::from_iter(language_line_file_names.into_iter())))
            .alias(new_column_name),
    ))
}

fn main() -> anyhow::Result<()> {
    let args: Args = Args::parse();

    for m3u8_name in M3U8_NAMES {
        let m3u8_path = args.m3u8_dir.join(format!("{}.m3u8", m3u8_name));
        if !m3u8_path.exists() {
            anyhow::bail!(
                "`{}` does not exist, exiting...",
                m3u8_path.to_string_lossy()
            );
        }
    }

    let m3u8_all_path: PathBuf = args.m3u8_dir.join(format!("{}.m3u8", M3U8_NAMES[0]));
    #[allow(clippy::result_filter_map)]
    let mut m3u8_all_lines = BufReader::new(File::open(&m3u8_all_path)?)
        .lines()
        // .flatten()
        .filter(Result::is_ok)
        .map(Result::unwrap) // would have used .flatten() but that has the danger of `flatten()` will run forever if the iterator repeatedly produces an `Err`
        .skip(1) // skip header line,
        .peekable();

    let mut line_ratings = Vec::new();
    let mut line_music_files = Vec::new();
    while m3u8_all_lines.peek().is_some() {
        let line_rating = m3u8_all_lines.next().unwrap();
        let line_music_file = m3u8_all_lines
            .next()
            .ok_or_else(|| anyhow::anyhow!(
                "{} is malformed: a rating line is not followed by a line describing the corresponding music file.",
                m3u8_all_path.to_string_lossy()
            ))?;
        line_ratings.push(line_rating);
        line_music_files.push(line_music_file);
    }
    let mut df = df!(
        "line_rating" => line_ratings,
        "line_file_name" => line_music_files,
    )?
    .lazy()
    .with_columns([
        col("line_rating")
            .str()
            .tail(lit(1))
            .strict_cast(DataType::UInt32) // see https://docs.pola.rs/user-guide/expressions/casting/#strings
            .alias("rating"),
        col("line_file_name")
            .str()
            .split(lit("/"))
            .list()
            .tail(lit(2))
            .alias("t"),
    ])
    // The following is courtesy of https://stackoverflow.com/a/72491642/7254995
    .select([
        all().exclude(["t"]),
        col("t").list().get(lit(0), false).alias("parent_dir"),
        col("t").list().get(lit(1), false).alias("file_name"),
    ]);

    for m3u8_name in &M3U8_NAMES[1..] {
        df = add_language_membership_column(
            df,
            args.m3u8_dir.join(format!("{}.m3u8", m3u8_name)),
            m3u8_name.split(" - ").last().unwrap(),
        )?;
    }

    let df = df.collect()?;
    print!("{:?}", df);

    Ok(())
}
