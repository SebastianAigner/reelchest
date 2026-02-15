import * as React from "react";
import {useMemo, useState} from "react";
import {Link, useHistory, useParams} from "react-router-dom";
import axios from "axios";
import {MainHeading, SectionHeading, SubHeading} from "../components/Typography";
import {StyledButton} from "../components/StyledButton";
import {useAbortableSWR} from "../hooks/useAbortableSWR";
import {fetcher} from "../utils";
import {commonStyles} from "../styles/common";
import {MediaLibraryEntry} from "../models/MediaLibraryEntry";

type ConversionMode = "IMMEDIATE" | "SCHEDULED";

type ConversionOptions = {
    maxWidth: number | null;
    maxHeight: number | null;
    fps: number | null;
    crf: number;
    audioBitrateKbps: number;
};

type ConversionQueueEntry = {
    id: string;
    sourceId: string;
    sourceTitle: string;
    mode: ConversionMode;
    status: "QUEUED" | "RUNNING";
    createdAt: number;
};

type ProblematicConversion = {
    id: string;
    sourceId: string;
    sourceTitle: string;
    error: string;
};

type ConversionSummary = {
    sourceId: string;
    alreadyConverted: boolean;
    queued: ConversionQueueEntry[];
    running?: ConversionQueueEntry;
    problematic: ProblematicConversion[];
};

const RESOLUTION_OPTIONS = [
    {label: "Original", maxWidth: null, maxHeight: null},
    {label: "1080p (1920×1080)", maxWidth: 1920, maxHeight: 1080},
    {label: "720p (1280×720)", maxWidth: 1280, maxHeight: 720},
    {label: "480p (854×480)", maxWidth: 854, maxHeight: 480},
];

const FPS_OPTIONS = [
    {label: "Original", fps: null},
    {label: "30 fps", fps: 30},
    {label: "60 fps", fps: 60},
];

function formatQueueEntry(entry: ConversionQueueEntry | undefined): string {
    if (!entry) return "";
    if (entry.status === "RUNNING") return "Running";
    return entry.mode === "SCHEDULED" ? "Scheduled" : "Queued";
}

export function Convert() {
    const {id} = useParams<{ id: string }>();
    const history = useHistory();
    const {data: entry} = useAbortableSWR<MediaLibraryEntry>(`/api/mediaLibrary/${id}`, fetcher);
    const {data: summary} = useAbortableSWR<ConversionSummary>(
        `/api/mediaLibrary/${id}/conversions`,
        fetcher,
        {refreshInterval: 2000}
    );
    const [resolutionLabel, setResolutionLabel] = useState(RESOLUTION_OPTIONS[1].label);
    const [fpsLabel, setFpsLabel] = useState(FPS_OPTIONS[1].label);
    const [crf, setCrf] = useState(21);
    const [audioBitrateKbps, setAudioBitrateKbps] = useState(160);
    const [mode, setMode] = useState<ConversionMode>("IMMEDIATE");
    const [convertAndDeleteOriginal, setConvertAndDeleteOriginal] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const [submitError, setSubmitError] = useState<string | null>(null);
    const [submitSuccess, setSubmitSuccess] = useState<string | null>(null);

    const resolutionOption = useMemo(
        () => RESOLUTION_OPTIONS.find(option => option.label === resolutionLabel) ?? RESOLUTION_OPTIONS[0],
        [resolutionLabel]
    );
    const fpsOption = useMemo(
        () => FPS_OPTIONS.find(option => option.label === fpsLabel) ?? FPS_OPTIONS[0],
        [fpsLabel]
    );

    const queuedCount = (summary?.queued?.length ?? 0) + (summary?.running ? 1 : 0);
    const hasQueueWarning = queuedCount > 0;
    const hasConversionWarning = summary?.alreadyConverted;
    const hasProblematic = (summary?.problematic?.length ?? 0) > 0;

    const handleSubmit = async () => {
        setIsSubmitting(true);
        setSubmitError(null);
        setSubmitSuccess(null);
        const options: ConversionOptions = {
            maxWidth: resolutionOption.maxWidth,
            maxHeight: resolutionOption.maxHeight,
            fps: fpsOption.fps,
            crf,
            audioBitrateKbps
        };
        try {
            await axios.post(`/api/mediaLibrary/${id}/conversions`, {
                mode,
                options,
                convertAndDeleteOriginal
            });
            setSubmitSuccess(mode === "SCHEDULED"
                ? "Conversion queued for the 3:00am batch."
                : "Conversion started and queued.");
        } catch (err: any) {
            const message = err?.response?.data?.error || err?.response?.data || err?.message || "Conversion failed.";
            setSubmitError(typeof message === "string" ? message : "Conversion failed.");
        } finally {
            setIsSubmitting(false);
        }
    };

    return (
        <>
            <MainHeading>Convert Video</MainHeading>
            <p className="text-sm text-gray-500 dark:text-gray-400">
                <Link to={`/movie/${id}`} className="underline">Back to entry</Link>
            </p>
            <SectionHeading>{entry?.name ?? "Loading entry..."}</SectionHeading>

            {(hasQueueWarning || hasConversionWarning) && (
                <div
                    className="my-4 rounded-xl border border-amber-300/60 bg-amber-50/80 px-4 py-3 text-amber-900 dark:border-amber-400/40 dark:bg-amber-900/30 dark:text-amber-100">
                    <strong className="block font-semibold">Heads up</strong>
                    {hasQueueWarning && (
                        <p className="text-sm">A conversion for this video is already queued or running.</p>
                    )}
                    {hasConversionWarning && (
                        <p className="text-sm">This video already has a converted version.</p>
                    )}
                </div>
            )}

            {hasProblematic && (
                <div
                    className="my-4 rounded-xl border border-red-300/60 bg-red-50/80 px-4 py-3 text-red-900 dark:border-red-400/40 dark:bg-red-900/30 dark:text-red-100">
                    <strong className="block font-semibold">Previous conversion errors</strong>
                    <p className="text-sm">There were issues with earlier conversions for this entry.</p>
                </div>
            )}

            <div
                className="my-6 grid gap-4 rounded-2xl border border-gray-200/70 bg-white/80 p-6 shadow-sm dark:border-gray-700/60 dark:bg-gray-900/60">
                <SubHeading>Settings</SubHeading>
                <label className="grid gap-2">
                    <span className="text-sm font-semibold">Resolution</span>
                    <select
                        value={resolutionLabel}
                        onChange={(event) => setResolutionLabel(event.target.value)}
                        className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-950"
                    >
                        {RESOLUTION_OPTIONS.map(option => (
                            <option key={option.label} value={option.label}>{option.label}</option>
                        ))}
                    </select>
                </label>
                <label className="grid gap-2">
                    <span className="text-sm font-semibold">Frame rate</span>
                    <select
                        value={fpsLabel}
                        onChange={(event) => setFpsLabel(event.target.value)}
                        className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-950"
                    >
                        {FPS_OPTIONS.map(option => (
                            <option key={option.label} value={option.label}>{option.label}</option>
                        ))}
                    </select>
                </label>
                <label className="grid gap-2">
                    <span className="text-sm font-semibold">CRF (18–28)</span>
                    <input
                        type="number"
                        min={18}
                        max={28}
                        step={1}
                        value={crf}
                        onChange={(event) => setCrf(Number(event.target.value))}
                        className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-950"
                    />
                </label>
                <label className="grid gap-2">
                    <span className="text-sm font-semibold">Audio bitrate (kbps)</span>
                    <input
                        type="number"
                        min={64}
                        max={320}
                        step={8}
                        value={audioBitrateKbps}
                        onChange={(event) => setAudioBitrateKbps(Number(event.target.value))}
                        className="rounded-lg border border-gray-300 bg-white px-3 py-2 text-sm dark:border-gray-700 dark:bg-gray-950"
                    />
                </label>
                <div className="grid gap-2">
                    <span className="text-sm font-semibold">Run mode</span>
                    <div className="flex flex-wrap gap-3">
                        <label className="flex items-center gap-2 text-sm">
                            <input
                                type="radio"
                                name="conversion-mode"
                                value="IMMEDIATE"
                                checked={mode === "IMMEDIATE"}
                                onChange={() => setMode("IMMEDIATE")}
                            />
                            Convert now
                        </label>
                        <label className="flex items-center gap-2 text-sm">
                            <input
                                type="radio"
                                name="conversion-mode"
                                value="SCHEDULED"
                                checked={mode === "SCHEDULED"}
                                onChange={() => setMode("SCHEDULED")}
                            />
                            Queue for 3:00am batch
                        </label>
                    </div>
                </div>
                <label
                    className="flex items-start gap-3 rounded-xl border border-rose-200/70 bg-rose-50/70 p-3 text-sm text-rose-900 dark:border-rose-400/40 dark:bg-rose-900/30 dark:text-rose-100">
                    <input
                        type="checkbox"
                        checked={convertAndDeleteOriginal}
                        onChange={(event) => setConvertAndDeleteOriginal(event.target.checked)}
                        className="mt-1"
                    />
                    <span>
                        <span className="block font-semibold">Convert and delete original</span>
                        The original entry will be marked for deletion after a successful conversion.
                    </span>
                </label>
                {summary?.running && (
                    <p className="text-xs text-gray-500 dark:text-gray-400">
                        Current status: {formatQueueEntry(summary.running)}.
                    </p>
                )}
            </div>

            {submitError && (
                <div
                    className="my-4 rounded-lg border border-red-300/60 bg-red-50/80 px-4 py-2 text-sm text-red-900 dark:border-red-400/40 dark:bg-red-900/30 dark:text-red-100">
                    {submitError}
                </div>
            )}
            {submitSuccess && (
                <div
                    className="my-4 rounded-lg border border-emerald-300/60 bg-emerald-50/80 px-4 py-2 text-sm text-emerald-900 dark:border-emerald-400/40 dark:bg-emerald-900/30 dark:text-emerald-100">
                    {submitSuccess}
                </div>
            )}

            <div className={commonStyles.verticalMargin}>
                <StyledButton onClick={handleSubmit} disabled={isSubmitting}>
                    {isSubmitting ? "Submitting..." : mode === "SCHEDULED" ? "Queue Conversion" : "Start Conversion"}
                </StyledButton>
                <StyledButton
                    className={`${commonStyles.standardMargin} ${commonStyles.inlineBlock}`}
                    onClick={() => history.push("/downloads")}
                >
                    View Queue
                </StyledButton>
            </div>
        </>
    );
}
