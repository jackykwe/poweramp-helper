use std::{
    fs::File,
    io::{BufRead, BufReader},
    path::PathBuf,
};

use clap::Parser;

#[derive(Parser)]
#[command(version, about, long_about = None)]
struct Args {
    /// Directory of m3u8 files. Note that these m3u8 files must exist and will be
    /// checked for existence: {All,Songs - {CHN,ENG,JAP,KOR,Others}}.m3u8
    #[arg(short = 'd', long)]
    m3u8_dir: PathBuf,
}

#[derive(Debug)]
enum Rating {
    ZeroStars,
    OneStar,
    TwoStars,
    ThreeStars,
    FourStars,
    FiveStars,
}

struct MusicFile {
    parentDirEncodedUri: String,
    fileName: String,
    rating: Rating,
    langEN: bool, // ENG
    langCN: bool, // CHN
    langJP: bool, // JAP
    langKR: bool, // KOR
    langO: bool,  // Others
    langCh: bool, // Choral
}

struct MusicFolder {
    encodedUri: String,
    dirName: String,
    doneMillis: u64, // null if not done
    resetMillis: u64, // null if never reset
                     // fileCount: i64,
                     // minusSum: i64,
                     // langChSum: i64,
                     // langCNSum: i64,
                     // langENSum: i64,
                     // langJPSum: i64,
                     // langKRSum: i64,
                     // langOSum: i64,
                     // pendingFirstSort: i64
                     // rating0SSum: i64,
                     // rating1SSum: i64,
                     // rating2SSum: i64,
                     // rating3SSum: i64,
                     // rating4SSum: i64,
                     // rating5SSum: i64
}

static M3U8_NAMES: [&str; 6] = [
    "All",
    "Songs - CHN",
    "Songs - ENG",
    "Songs - JAP",
    "Songs - KOR",
    "Songs - Others",
];

fn main() -> anyhow::Result<()> {
    let args = Args::parse();

    for m3u8_name in M3U8_NAMES {
        let m3u8_path = args.m3u8_dir.join(format!("{}.m3u8", m3u8_name));
        if !m3u8_path.exists() {
            anyhow::bail!(
                "`{}` does not exist, exiting...",
                m3u8_path.to_string_lossy()
            );
        }
    }
    let m3u8_all_path = args.m3u8_dir.join(format!("{}.m3u8", M3U8_NAMES[0]));
    let mut m3u8_all_lines = BufReader::new(File::open(&m3u8_all_path)?)
        .lines()
        .flatten() // drop Result::Err lines
        .skip(1) // skip header line,
        .peekable();

    while m3u8_all_lines.peek().is_some() {
        let line_rating = m3u8_all_lines.next().unwrap();
        let line_music_file = m3u8_all_lines.next().expect(&format!("{} is malformed: a rating line ({}) is not followed by a line describing the corresponding music file.", m3u8_all_path.to_string_lossy(), line_rating));

        let rating = match line_rating.chars().last().expect(&format!(
            "{} is malformed: a rating line ({}) is empty",
            m3u8_all_path.to_string_lossy(),
            line_rating
        )) {
            '0' => Rating::ZeroStars,
            '1' => Rating::OneStar,
            '2' => Rating::TwoStars,
            '3' => Rating::ThreeStars,
            '4' => Rating::FourStars,
            '5' => Rating::FiveStars,
            _ => anyhow::bail!(
                "{} is malformed: a rating line ({}) contains an invalid rating",
                m3u8_all_path.to_string_lossy(),
                line_rating
            ),
        };
        println!("{:?} {}", rating, line_music_file);
        break;
    }

    Ok(())
}
