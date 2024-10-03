use std::{
    fs::File,
    io::{BufRead, BufReader, BufWriter, Write},
    iter::Peekable,
    path::{Path, PathBuf},
};

use clap::Parser;
use polars::{
    df,
    prelude::{all, col, concat_str, lit, DataType, Expr, IntoLazy, LazyFrame},
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

    /// Do not write `[Auto] <...>.m3u8` playlists into `m3u8_dir`, only display
    /// analysis table.
    #[arg(long)]
    analysis_only: bool,
}

static M3U8_NAMES: [&str; 6] = [
    "All",
    "Songs - CN",
    "Songs - EN",
    "Songs - JP",
    "Songs - KR",
    "Songs - O",
];

fn check_required_m3u8s_exist(m3u8_dir: &Path) -> anyhow::Result<()> {
    for m3u8_name in M3U8_NAMES {
        let m3u8_path = m3u8_dir.join(format!("{}.m3u8", m3u8_name));
        if !m3u8_path.exists() {
            anyhow::bail!(
                "`{}` does not exist, exiting...",
                m3u8_path.to_string_lossy()
            );
        }
    }
    Ok(())
}

fn read_and_parse_m3u8_all(m3u8_dir: &Path) -> anyhow::Result<LazyFrame> {
    /// File reader utility.
    fn read_m3u8_file_as_lines(
        file_path: &Path,
        head_lines_to_drop: usize,
    ) -> anyhow::Result<Peekable<impl Iterator<Item = String>>> {
        #[allow(clippy::result_filter_map)]
        Ok(BufReader::new(File::open(file_path)?)
            .lines()
            // .flatten()
            .filter(Result::is_ok)
            .map(Result::unwrap) // would have used .flatten() but that has the danger of `flatten()` will run forever if the iterator repeatedly produces an `Err`
            .skip(head_lines_to_drop)
            .peekable())
    }

    fn read_and_add_language_membership_column(
        df: LazyFrame,
        m3u8_language_path: PathBuf,
        new_column_name: &str,
    ) -> anyhow::Result<LazyFrame> {
        #[allow(clippy::result_filter_map)]
        let mut m3u8_language_lines = read_m3u8_file_as_lines(&m3u8_language_path, 1)?;

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

    let m3u8_all_path: PathBuf = m3u8_dir.join(format!("{}.m3u8", M3U8_NAMES[0]));
    let mut m3u8_all_lines = read_m3u8_file_as_lines(&m3u8_all_path, 1)?;
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
        df = read_and_add_language_membership_column(
            df,
            m3u8_dir.join(format!("{}.m3u8", m3u8_name)),
            m3u8_name.split(" - ").last().unwrap(),
        )?;
    }
    Ok(df)
}

/// `df` is a table of all the music files, one row per music file. It must minimally
/// contain the `line_rating` and `line_file_name` columns. Use `df_filter` to select
/// the music files to write to a new playlist at `destination_path`. `force`, if true,
/// allows the write to overwrite an existing playlist at `destination_path` (if false,
/// user confirmation is requested).
fn generate_and_write_auto_m3u8_playlist(
    df: LazyFrame,
    df_filter: Expr, // rows to write to `destination_path`
    destination_path: &Path,
    force: bool,
) -> anyhow::Result<()> {
    /// Prompts the user whether to proceed with overwriting an existing file at `file_path`.
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

    let mut proceed_with_writing = true;
    if destination_path.exists() && !force && !prompt_overwrite_file(destination_path)? {
        proceed_with_writing = false;
    }
    if proceed_with_writing {
        let mut m3u8_content = String::from("#EXTM3U\n");
        let m3u8_content_tail = df
            .filter(df_filter)
            .sort(["line_file_name"], Default::default())
            .select([
                concat_str([col("line_rating"), col("line_file_name")], "\n", false) // merge two columns
                    .str()
                    .join("\n", false) // merge all rows
                    .get(lit(0))
                    .alias("t"),
            ])
            .collect()?;
        let m3u8_content_tail = m3u8_content_tail
            .select_at_idx(0) // select first and only column (as Series)
            .unwrap() // existence of first column guaranteed by select() above
            .str()? // unwrap type to underlying ChunkedArray<StringType>
            .get(0);
        if let Some(m3u8_songs_content_tail) = m3u8_content_tail {
            m3u8_content.push_str(m3u8_songs_content_tail);
        }
        let mut f: BufWriter<File> = BufWriter::new(File::create(destination_path)?);
        f.write_all(m3u8_content.as_bytes())?;
    }
    Ok(())
}

fn main() -> anyhow::Result<()> {
    let args: Args = Args::parse();
    check_required_m3u8s_exist(&args.m3u8_dir)?;

    // "Single source of truth"
    let df = read_and_parse_m3u8_all(&args.m3u8_dir)?;

    // Write `[Auto] <...>.m3u8` playlists
    if !args.analysis_only {
        generate_and_write_auto_m3u8_playlist(
            df.clone(),
            col("CN")
                .or(col("EN"))
                .or(col("JP"))
                .or(col("KR"))
                .or(col("O")),
            &args.m3u8_dir.join("[Auto] Songs.m3u8"),
            args.force,
        )?;

        for stars in 1..=5 {
            generate_and_write_auto_m3u8_playlist(
                df.clone(),
                col("rating").eq(lit(stars)),
                &args.m3u8_dir.join(format!("[Auto] {}S.m3u8", stars)),
                args.force,
            )?;
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
