use std::{
    collections::VecDeque,
    fs::File,
    io::{BufRead, BufReader, BufWriter, Read, Write},
    iter::Peekable,
    path::{Path, PathBuf},
};

use clap::Parser;
use polars::{
    df,
    io::SerWriter,
    prelude::{all, col, concat_str, lit, DataType, Expr, IntoLazy, IntoVec, LazyFrame},
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
            .strict_cast(DataType::UInt8) // see https://docs.pola.rs/user-guide/expressions/casting/#strings
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

fn materialise_lazyframe_into_csv_lines(df: LazyFrame) -> anyhow::Result<VecDeque<u8>> {
    let mut statistics_table = df
        .clone()
        .group_by(["parent_dir"])
        .agg([
            col("rating").eq(lit(0)).sum().alias("0S"),
            col("rating").eq(lit(1)).sum().alias("1S"),
            col("rating").eq(lit(2)).sum().alias("2S"),
            col("rating").eq(lit(3)).sum().alias("3S"),
            col("rating").eq(lit(4)).sum().alias("4S"),
            col("rating").eq(lit(5)).sum().alias("5S"),
            col("EN").sum(),
            col("CN").sum(),
            col("JP").sum(),
            col("KR").sum(),
            col("O").sum(),
            col("file_name").count().alias("Total"),
        ])
        // no need to .select([all().exclude([...])]) because agg() implicitly only
        // selects the provided columns above
        .sort(["parent_dir"], Default::default())
        .rename(["parent_dir"], ["Directory"])
        .collect()?;

    // Export DataFrame to csv format for quicker row-wise iteration (polars supports
    // only fast column-wise iteration since data is stored column-wise), as recommended
    // by polars's documentation.
    let mut in_memory = VecDeque::with_capacity(statistics_table.estimated_size());
    polars::prelude::CsvWriter::new(BufWriter::new(&mut in_memory))
        .include_header(true)
        .with_separator(b',')
        .with_quote_char(b'"')
        .with_quote_style(polars::prelude::QuoteStyle::Always)
        .finish(&mut statistics_table)?;
    Ok(in_memory)
}

fn get_sealed_parent_dirs() -> anyhow::Result<Option<Vec<String>>> {
    // Read sealed.txt if it exists
    let sealed_txt_path = PathBuf::from("sealed.txt");
    Ok(if sealed_txt_path.exists() {
        Some(
            #[allow(clippy::result_filter_map)]
            BufReader::new(File::open("sealed.txt")?)
                .lines()
                .filter(Result::is_ok)
                .map(Result::unwrap) // would have used .flatten() but that has the danger of `flatten()` will run forever if the iterator repeatedly produces an `Err`
                .collect(),
        )
    } else {
        None
    })
}

fn construct_pretty_table(
    csv_lines: impl Read,
    sealed_parent_dirs: &Option<Vec<String>>,
) -> anyhow::Result<comfy_table::Table> {
    fn red_cell_if_non_zero(value: usize, cell_suffix: Option<&str>) -> comfy_table::Cell {
        let displayed = match cell_suffix {
            None => format!("{}", value),
            Some(cell_suffix) => format!("{}{}", value, cell_suffix),
        };
        if value == 0 {
            comfy_table::Cell::new(displayed)
        } else {
            comfy_table::Cell::new(displayed)
                .fg(comfy_table::Color::Red)
                .add_attributes(vec![
                    comfy_table::Attribute::Bold,
                    comfy_table::Attribute::SlowBlink,
                ])
        }
    }

    /// Would have used `&str` in argument types instead of String but that makes things
    /// harder for calling code. Perhaps something to fix in the future (not necessary now
    /// since the calling flexibility gained from using `&str` isn't required at the moment;
    /// cf. deref coercion). The problem is that `Vec<&str>` is hard to obtain from a
    /// `Vec<String>`.
    fn red_cell_if_not_in(
        display_value: String,
        parent_dir: &String,
        sealed_parent_dirs: &Option<Vec<String>>,
    ) -> comfy_table::Cell {
        match sealed_parent_dirs {
            Some(sealed_parent_dirs) if !sealed_parent_dirs.contains(parent_dir) => {
                comfy_table::Cell::new(display_value)
                    .fg(comfy_table::Color::Red)
                    .add_attributes(vec![
                        comfy_table::Attribute::Bold,
                        comfy_table::Attribute::RapidBlink,
                    ])
            }
            Some(_) | None => comfy_table::Cell::new(display_value),
        }
    }

    let mut pretty_table = comfy_table::Table::new();
    pretty_table
        .load_preset(comfy_table::presets::UTF8_FULL)
        .apply_modifier(comfy_table::modifiers::UTF8_ROUND_CORNERS)
        .set_content_arrangement(comfy_table::ContentArrangement::DynamicFullWidth)
        .set_header([
            comfy_table::Cell::new("Directory").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("0S").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("1S").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("2S").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("3S").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("4S").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("5S").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("EN").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("CN").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("JP").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("KR").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("O").add_attribute(comfy_table::Attribute::Bold),
            comfy_table::Cell::new("Total").add_attribute(comfy_table::Attribute::Bold),
        ]);
    for row in csv::Reader::from_reader(BufReader::new(csv_lines))
        .records()
        .flatten()
    {
        // "Directory","0S","1S","2S","3S","4S","5S","EN","CN","JP","KR","O","Total"
        let row = row.into_vec();
        let parent_dir = &row[0].to_string();
        let zero_stars_count: usize = row[1].parse()?;
        let one_star_count: usize = row[2].parse()?;
        let two_stars_count: usize = row[3].parse()?;
        let three_stars_count: usize = row[4].parse()?;
        let four_stars_count: usize = row[5].parse()?;
        let five_stars_count: usize = row[6].parse()?;
        let en_count: usize = row[7].parse()?;
        let cn_count: usize = row[8].parse()?;
        let jp_count: usize = row[9].parse()?;
        let kr_count: usize = row[10].parse()?;
        let o_count: usize = row[11].parse()?;
        let total_count: usize = row[12].parse()?;
        pretty_table.add_row([
            comfy_table::Cell::new(parent_dir),
            red_cell_if_non_zero(zero_stars_count, Some(" 零")),
            comfy_table::Cell::new(format!("{} 壱", one_star_count)),
            comfy_table::Cell::new(format!("{} 弐", two_stars_count)),
            comfy_table::Cell::new(format!("{} 参", three_stars_count)),
            comfy_table::Cell::new(format!("{} 肆", four_stars_count)),
            comfy_table::Cell::new(format!("{} 伍", five_stars_count)),
            red_cell_if_not_in(format!("{} E", en_count), parent_dir, sealed_parent_dirs),
            red_cell_if_not_in(format!("{} 中", cn_count), parent_dir, sealed_parent_dirs),
            red_cell_if_not_in(format!("{} 日", jp_count), parent_dir, sealed_parent_dirs),
            red_cell_if_not_in(format!("{} 한", kr_count), parent_dir, sealed_parent_dirs),
            red_cell_if_not_in(format!("{} O", o_count), parent_dir, sealed_parent_dirs),
            comfy_table::Cell::new(format!("{} Σ", total_count)),
        ]);
    }
    for column_index in 1..=12 {
        pretty_table
            .column_mut(column_index)
            .unwrap()
            .set_cell_alignment(comfy_table::CellAlignment::Right);
    }
    Ok(pretty_table)
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

    let df_csv = materialise_lazyframe_into_csv_lines(df)?;
    let sealed_parent_dirs = get_sealed_parent_dirs()?;
    let pretty_table = construct_pretty_table(df_csv, &sealed_parent_dirs)?;

    println!("{}", pretty_table);
    Ok(())
}
