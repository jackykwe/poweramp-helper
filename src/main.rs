use std::{
    fs::File,
    io::{BufRead, BufReader, BufWriter, Write},
    path::{Path, PathBuf},
};

use clap::Parser;
use polars::{
    df,
    prelude::{all, col, concat_str, lit, DataType, IntoLazy, LazyFrame},
    series::Series,
};

#[derive(Parser)]
#[command(version, about, long_about = None)]
struct Args {
    /// Directory of m3u8 files. Note that these m3u8 files must exist and will be
    /// checked for existence: {All,Songs - {CHN,ENG,JAP,KOR,Others}}.m3u8
    #[arg(short = 'd', long)]
    m3u8_dir: PathBuf,

    /// Automatically overwrite `[Auto] <...>.m3u8` playlists in `m3u8_dir` if they
    /// exist, without prompting.
    #[arg(short = 'y')]
    force: bool,
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
            .is_in(lit(Series::from_iter(language_line_file_names)))
            .alias(new_column_name),
    ))
}

fn prompt_overwrite_file(file_path: &Path) -> anyhow::Result<bool> {
    match dialoguer::Input::<char>::new()
        .with_prompt(format!(
            "`{}` exists. Overwrite [y/N]?",
            file_path.to_string_lossy()
        ))
        .allow_empty(true)
        .default('N')
        .show_default(false)
        .validate_with(|input: &char| match input {
            'y' | 'Y' | 'n' | 'N' => Ok(()),
            _ => Err("Type either `y` or `n`"),
        })
        .interact_text()?
    {
        'y' | 'Y' => Ok(true),
        'n' | 'N' => Ok(false),
        _ => panic!(), // impossible due to validation above
    }
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
    let df = df; // remove mutability

    let m3u8_songs_path = args.m3u8_dir.join("[Auto] Songs.m3u8");
    let mut write_m3u8_songs = true;
    if m3u8_songs_path.exists() && !args.force && !prompt_overwrite_file(&m3u8_songs_path)? {
        write_m3u8_songs = false;
    }
    if write_m3u8_songs {
        let mut m3u8_songs_content = String::from("#EXTM3U\n");
        let m3u8_songs_content_tail = df
            .clone()
            .filter(
                col("CN")
                    .or(col("EN"))
                    .or(col("JP"))
                    .or(col("KR"))
                    .or(col("O")),
            )
            .sort(["line_file_name"], Default::default())
            .select([
                concat_str([col("line_rating"), col("line_file_name")], "\n", false) // merge two columns
                    .str()
                    .join("\n", false) // merge all rows
                    .get(lit(0))
                    .alias("t"),
            ])
            .collect()?;
        let m3u8_songs_content_tail = m3u8_songs_content_tail
            .select_at_idx(0) // select first and only column (as Series)
            .unwrap() // existence of first column guaranteed by select() above
            .str()? // unwrap type to underlying ChunkedArray<StringType>
            .get(0);
        if let Some(m3u8_songs_content_tail) = m3u8_songs_content_tail {
            m3u8_songs_content.push_str(m3u8_songs_content_tail);
        }
        let mut m3u8_songs = BufWriter::new(File::create(m3u8_songs_path)?);
        m3u8_songs.write_all(m3u8_songs_content.as_bytes())?;
    }

    for stars in 1..=5 {
        let m3u8_stars_path = args.m3u8_dir.join(format!("[Auto] {}S.m3u8", stars));
        let mut write_m3u8_stars: bool = true;
        if m3u8_stars_path.exists() && !args.force && !prompt_overwrite_file(&m3u8_stars_path)? {
            write_m3u8_stars = false;
        }
        if write_m3u8_stars {
            let mut m3u8_stars_content = String::from("#EXTM3U\n");
            let m3u8_stars_content_tail = df
                .clone()
                .filter(col("rating").eq(lit(stars)))
                .sort(["line_file_name"], Default::default())
                .select([
                    concat_str([col("line_rating"), col("line_file_name")], "\n", false) // merge two columns
                        .str()
                        .join("\n", false) // merge all rows
                        .get(lit(0))
                        .alias("t"),
                ])
                .collect()?;
            let m3u8_stars_content_tail = m3u8_stars_content_tail
                .select_at_idx(0) // select first and only column (as Series)
                .unwrap() // existence of first column guaranteed by select() above
                .str()? // unwrap type to underlying ChunkedArray<StringType>
                .get(0);
            if let Some(m3u8_stars_content_tail) = m3u8_stars_content_tail {
                m3u8_stars_content.push_str(m3u8_stars_content_tail);
            }
            let mut m3u8_stars = BufWriter::new(File::create(m3u8_stars_path)?);
            m3u8_stars.write_all(m3u8_stars_content.as_bytes())?;
        }
    }

    let statistics_table = df
        .clone()
        .group_by(["parent_dir"])
        .agg([
            col("rating").eq(lit(0)).sum().alias("0S"),
            col("rating").eq(lit(1)).sum().alias("1S"),
            col("rating").eq(lit(2)).sum().alias("2S"),
            col("rating").eq(lit(3)).sum().alias("3S"),
            col("rating").eq(lit(4)).sum().alias("4S"),
            col("rating").eq(lit(5)).sum().alias("5S"),
            col("CN").sum(),
            col("EN").sum(),
            col("JP").sum(),
            col("KR").sum(),
            col("O").sum(),
            col("file_name").count().alias("Total"),
        ])
        // no need to .select([all().exclude([...])]) because agg() implicitly only
        // selects the provided columns above
        .sort(["parent_dir"], Default::default())
        .collect()?;

    println!("{}", statistics_table);

    Ok(())
}
