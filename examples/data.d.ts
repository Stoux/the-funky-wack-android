
// API: GET https://tfw.stoux.nl/api/editions => Edition[]

export type Edition = {
    id: number,
    number: string,
    tag_line?: string,
    date?: string,
    notes?: string,
    livesets?: Liveset[],
    empty_note?: string,
    timetabler_mode: boolean,
    poster_url?: string,
    poster_srcset_urls?: {
        url: string,
        width: number,
    }[],
}


/**
 * Key is the quality, value is the URL to the file
 */
export type LivesetFilesByQuality = {
    lq?: string,
    hq?: string,
    lossless?: string,
};

export type LivesetQuality = keyof LivesetFilesByQuality;

export type Liveset = {
    id: number,
    edition_id: number,
    edition?: Edition,
    title: string,
    artist_name: string,
    description?: string,
    bpm?: string,
    genre?: string,
    duration_in_seconds?: number,
    started_at?: string,
    lineup_order?: number,
    /** null = no timetable configured, false = invalid timetable (missing data), string = timetable slot (start to end times) */
    timeslot?: null|false|string,
    soundcloud_url?: string,
    audio_waveform_path?: string,
    audio_waveform_url?: string,
    tracks?: LivesetTrack[],
    files?: LivesetFilesByQuality
}

export type LivesetTrack = {
    id: number,
    liveset_id: number,
    title: string,
    // Timestamp in seconds from start of liveset
    timestamp: number|null,
    order: number,
}