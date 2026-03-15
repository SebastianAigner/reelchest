import * as React from "react";
import {Link} from "react-router-dom";
import {useAbortableSWR} from "../hooks/useAbortableSWR";
import {fetcher} from "../utils";
import {MainHeading, SectionHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import {StyledButton} from "../components/StyledButton";
import {ArrowsRightLeftIcon} from "@heroicons/react/24/solid";

type MediaInformationSummary = {
    id: string;
    title: string;
    originUrl: string;
    mimeType?: string | null;
    width?: number | null;
    height?: number | null;
    frameRate?: number | null;
    fileSize?: number | null;
    bitRate?: number | null;
    durationSeconds?: number | null;
};

type ConversionMode = "IMMEDIATE" | "SCHEDULED";
type ConversionStatus = "QUEUED" | "RUNNING";

type ConversionQueueEntry = {
    id: string;
    sourceId: string;
    sourceTitle: string;
    mode: ConversionMode;
    status: ConversionStatus;
    createdAt: number;
};

type ConversionQueueSnapshot = {
    queued: ConversionQueueEntry[];
    running?: ConversionQueueEntry | null;
    problematic?: Array<{ id: string }>;
};

type ConversionBatchResult = {
    total: number;
    enqueued: number;
    skipped: number;
    missing: number;
};

const DEFAULT_MEDIA_QUERY = "sort:resolution desc limit 10";
const MEDIA_QUERY_FIELDS = "resolution, frameRate, fileSize, bitRate, duration, width, height";

const SORT_ALIASES: Record<string, string> = {
    resolution: "resolution",
    res: "resolution",
    framerate: "frameRate",
    "frame-rate": "frameRate",
    fps: "frameRate",
    filesize: "fileSize",
    "file-size": "fileSize",
    size: "fileSize",
    bitrate: "bitRate",
    "bit-rate": "bitRate",
    br: "bitRate",
    duration: "duration",
    length: "duration",
    width: "width",
    w: "width",
    height: "height",
    h: "height"
};

type ParsedMediaQuery = {
    sort: string;
    order: string;
    limit: number;
    excludedTerms: string[];
    errors: string[];
};

function parseMediaQuery(input: string): ParsedMediaQuery {
    const errors: string[] = [];
    let sort = "resolution";
    let order = "desc";
    let limit = 10;
    const excludedTerms: string[] = [];

    const tokens = input.trim().split(/\s+/).filter(Boolean);
    for (let i = 0; i < tokens.length; i++) {
        const rawToken = tokens[i];
        const token = rawToken.toLowerCase();
        if (token.startsWith("-") && token.length > 1) {
            const term = token.slice(1).trim();
            if (term.length > 0) {
                excludedTerms.push(term);
            }
            continue;
        }
        if (token.startsWith("sort:") || token.startsWith("sort=")) {
            const value = token.split(/[:=]/)[1];
            const normalized = SORT_ALIASES[value];
            if (normalized) {
                sort = normalized;
            } else {
                errors.push(`Unknown sort key: ${value}`);
            }
            continue;
        }
        if (token === "sort" && tokens[i + 1]) {
            const value = tokens[i + 1].toLowerCase();
            const normalized = SORT_ALIASES[value];
            if (normalized) {
                sort = normalized;
                i++;
            } else {
                errors.push(`Unknown sort key: ${value}`);
            }
            continue;
        }
        if (token.startsWith("limit:") || token.startsWith("limit=")) {
            const value = token.split(/[:=]/)[1];
            const parsed = Number.parseInt(value, 10);
            if (Number.isFinite(parsed) && parsed > 0) {
                limit = parsed;
            } else {
                errors.push(`Invalid limit: ${value}`);
            }
            continue;
        }
        if (token === "limit" && tokens[i + 1]) {
            const value = tokens[i + 1];
            const parsed = Number.parseInt(value, 10);
            if (Number.isFinite(parsed) && parsed > 0) {
                limit = parsed;
                i++;
            } else {
                errors.push(`Invalid limit: ${value}`);
            }
            continue;
        }
        if (token.startsWith("order:") || token.startsWith("order=")) {
            const value = token.split(/[:=]/)[1];
            if (value === "asc" || value === "desc") {
                order = value;
            } else {
                errors.push(`Unknown order: ${value}`);
            }
            continue;
        }
        if (token === "asc" || token === "desc") {
            order = token;
            continue;
        }
    }

    return {
        sort,
        order,
        limit,
        excludedTerms: Array.from(new Set(excludedTerms)),
        errors
    };
}

function useMediaLibraryEntries() {
    const {data, error, mutate} = useAbortableSWR<Array<AutoTaggedMediaLibraryEntry>>(
        "/api/mediaLibrary?auto=true",
        fetcher
    );

    return {
        entries: data || [],
        isLoading: !data && !error,
        isError: error,
        refresh: mutate
    };
}

function useDiskUsage() {
    const {data, error} = useAbortableSWR<{
        path: string;
        totalBytes: number;
        freeBytes: number;
        usableBytes: number;
    }>("/api/mediaLibrary/disk-usage", fetcher);

    return {
        usage: data,
        isLoading: !data && !error,
        isError: error
    };
}

type ApplicationConfig = {
    shutdownUrl: string;
};

function useApplicationConfig() {
    const {data, error} = useAbortableSWR<ApplicationConfig>("/api/config", fetcher);

    return {
        config: data,
        isLoading: !data && !error,
        isError: error
    };
}

type OrphanScanResult = {
    orphansFound: number;
    orphansDeleted: number;
    failedDeletions: number;
    bytesFreed: number;
};

type OrphanScanProgress = OrphanScanResult & {
    status: "idle" | "running" | "completed" | "failed";
    totalTombstones: number;
    processed: number;
    startedAt?: number;
    finishedAt?: number;
    error?: string;
};

type MediaInfoPopulationProgress = {
    status: "idle" | "running" | "completed" | "failed";
    totalEntries: number;
    processed: number;
    probed: number;
    skipped: number;
    succeeded: number;
    failed: number;
    startedAt?: number;
    finishedAt?: number;
    error?: string;
};

function formatBytes(bytes: number): string {
    if (!Number.isFinite(bytes)) return "0 B";
    if (bytes < 1024) return `${bytes} B`;
    const units = ["KB", "MB", "GB", "TB"];
    let value = bytes / 1024;
    let unitIndex = 0;
    while (value >= 1024 && unitIndex < units.length - 1) {
        value /= 1024;
        unitIndex++;
    }
    return `${value.toFixed(value >= 10 ? 1 : 2)} ${units[unitIndex]}`;
}

function formatResolution(width?: number | null, height?: number | null): string {
    if (!width || !height) return "—";
    return `${width}×${height}`;
}

function formatFrameRate(frameRate?: number | null): string {
    if (!frameRate || frameRate <= 0) return "—";
    return `${frameRate.toFixed(2)} fps`;
}

function formatBitRate(bitRate?: number | null): string {
    if (!bitRate || bitRate <= 0) return "—";
    const kbps = bitRate / 1000;
    if (kbps >= 1000) return `${(kbps / 1000).toFixed(2)} Mbps`;
    return `${Math.round(kbps)} kbps`;
}

function formatDurationSeconds(durationSeconds?: number | null): string {
    if (!durationSeconds || durationSeconds <= 0) return "—";
    const totalSeconds = Math.floor(durationSeconds);
    const hrs = Math.floor(totalSeconds / 3600);
    const mins = Math.floor((totalSeconds % 3600) / 60);
    const secs = totalSeconds % 60;
    return `${hrs.toString().padStart(2, "0")}:${mins.toString().padStart(2, "0")}:${secs
        .toString()
        .padStart(2, "0")}`;
}

function formatOptionalBytes(bytes?: number | null): string {
    if (!bytes || bytes <= 0) return "—";
    return formatBytes(bytes);
}

function formatTimestamp(timestamp?: number): string {
    if (!timestamp) return "unknown";
    return new Date(timestamp).toLocaleString();
}

export function Admin() {
    const {entries, isLoading, isError, refresh} = useMediaLibraryEntries();
    const {usage, isLoading: isDiskLoading, isError: isDiskError} = useDiskUsage();
    const {config, isLoading: isConfigLoading, isError: isConfigError} = useApplicationConfig();
    const markedForDeletionCount = React.useMemo(() => {
        return entries.filter(entry => entry.mediaLibraryEntry.markedForDeletion).length;
    }, [entries]);
    const [scanProgress, setScanProgress] = React.useState<OrphanScanProgress | null>(null);
    const [scanError, setScanError] = React.useState<string | null>(null);
    const [isScanning, setIsScanning] = React.useState(false);
    const [mediaInfoProgress, setMediaInfoProgress] = React.useState<MediaInfoPopulationProgress | null>(null);
    const [mediaInfoPopulateError, setMediaInfoPopulateError] = React.useState<string | null>(null);
    const [isPopulatingMediaInfo, setIsPopulatingMediaInfo] = React.useState(false);
    const [deleteError, setDeleteError] = React.useState<string | null>(null);
    const [deleteStatus, setDeleteStatus] = React.useState<string | null>(null);
    const [isDeleting, setIsDeleting] = React.useState(false);
    const [shutdownError, setShutdownError] = React.useState<string | null>(null);
    const [shutdownStatus, setShutdownStatus] = React.useState<string | null>(null);
    const [isShuttingDown, setIsShuttingDown] = React.useState(false);
    const [mediaQuery, setMediaQuery] = React.useState(DEFAULT_MEDIA_QUERY);
    const parsedMediaQuery = React.useMemo(() => parseMediaQuery(mediaQuery), [mediaQuery]);
    const [isDefaultConverting, setIsDefaultConverting] = React.useState(false);
    const [defaultConvertStatus, setDefaultConvertStatus] = React.useState<string | null>(null);
    const [defaultConvertError, setDefaultConvertError] = React.useState<string | null>(null);
    const [isClearingConversions, setIsClearingConversions] = React.useState(false);
    const [clearConversionsStatus, setClearConversionsStatus] = React.useState<string | null>(null);
    const [clearConversionsError, setClearConversionsError] = React.useState<string | null>(null);
    const mediaInfoQueryString = React.useMemo(() => {
        const params = new URLSearchParams({
            sort: parsedMediaQuery.sort,
            order: parsedMediaQuery.order,
            limit: String(parsedMediaQuery.limit)
        });
        parsedMediaQuery.excludedTerms.forEach((term) => {
            params.append("exclude", term);
        });
        return params.toString();
    }, [parsedMediaQuery.excludedTerms, parsedMediaQuery.limit, parsedMediaQuery.order, parsedMediaQuery.sort]);
    const mediaInfoEndpoint = React.useMemo(() => {
        return `/api/mediaLibrary/media-information/search?${mediaInfoQueryString}`;
    }, [mediaInfoQueryString]);
    const {data: mediaInfoResults, error: mediaInfoError} = useAbortableSWR<Array<MediaInformationSummary>>(
        mediaInfoEndpoint,
        fetcher
    );
    const {data: conversionQueue} = useAbortableSWR<ConversionQueueSnapshot>(
        "/api/conversions",
        fetcher,
        {refreshInterval: 2000}
    );
    const isMediaInfoLoading = !mediaInfoResults && !mediaInfoError;
    const conversionBySource = React.useMemo(() => {
        const items = [
            ...(conversionQueue?.queued ?? []),
            ...(conversionQueue?.running ? [conversionQueue.running] : [])
        ];
        const map = new Map<string, ConversionQueueEntry>();
        items.forEach(item => {
            if (!map.has(item.sourceId)) {
                map.set(item.sourceId, item);
            }
        });
        return map;
    }, [conversionQueue]);

    const queuedConversionCount = conversionQueue?.queued?.length ?? 0;
    const runningConversionCount = conversionQueue?.running ? 1 : 0;
    const problematicConversionCount = conversionQueue?.problematic?.length ?? 0;

    const clearConversionQueue = async () => {
        const total = queuedConversionCount + problematicConversionCount + runningConversionCount;
        if (total === 0) {
            setClearConversionsStatus("No conversions to clear.");
            return;
        }
        const confirmed = window.confirm(
            `Clear ${queuedConversionCount} queued conversion${queuedConversionCount === 1 ? "" : "s"} ` +
            `and ${problematicConversionCount} failed conversion${problematicConversionCount === 1 ? "" : "s"}?` +
            (runningConversionCount > 0 ? " Running conversions will be cancelled." : "")
        );
        if (!confirmed) return;

        setIsClearingConversions(true);
        setClearConversionsError(null);
        setClearConversionsStatus(null);
        try {
            const response = await fetch("/api/conversions/clear", {method: "POST"});
            if (!response.ok) {
                throw new Error(`Request failed with ${response.status}`);
            }
            const data = await response.json() as ConversionQueueSnapshot;
            const runningSuffix = data.running
                ? " Running conversion still active."
                : runningConversionCount > 0
                    ? " Running conversions cancelled."
                    : "";
            setClearConversionsStatus(`Cleared conversion queue.${runningSuffix}`);
        } catch (err) {
            setClearConversionsError(err instanceof Error ? err.message : "Failed to clear conversion queue.");
        } finally {
            setIsClearingConversions(false);
        }
    };
    const scanProgressPercent = React.useMemo(() => {
        if (!scanProgress) return 0;
        if (scanProgress.totalTombstones > 0) {
            return Math.min(
                100,
                Math.round((scanProgress.processed / scanProgress.totalTombstones) * 100)
            );
        }
        return scanProgress.status === "running" ? 0 : 100;
    }, [scanProgress]);
    const mediaInfoProgressPercent = React.useMemo(() => {
        if (!mediaInfoProgress) return 0;
        if (mediaInfoProgress.totalEntries > 0) {
            return Math.min(
                100,
                Math.round((mediaInfoProgress.processed / mediaInfoProgress.totalEntries) * 100)
            );
        }
        return mediaInfoProgress.status === "running" ? 0 : 100;
    }, [mediaInfoProgress]);

    const fetchOrphanScanStatus = React.useCallback(async () => {
        const response = await fetch("/api/mediaLibrary/debug/scan-orphans");
        if (!response.ok) {
            throw new Error(`Request failed with ${response.status}`);
        }
        const data = await response.json();
        setScanProgress(data);
        if (data.status === "running") {
            setIsScanning(true);
            return;
        }
        setIsScanning(false);
        if (data.status === "failed") {
            setScanError(data.error ?? "Failed to scan for orphans.");
        }
    }, []);

    const runOrphanScan = async () => {
        setIsScanning(true);
        setScanError(null);
        setScanProgress(null);
        try {
            const response = await fetch("/api/mediaLibrary/debug/scan-orphans", {
                method: "POST"
            });
            if (!response.ok) {
                throw new Error(`Request failed with ${response.status}`);
            }
            const data = await response.json();
            setScanProgress(data);
            if (data.status !== "running") {
                setIsScanning(false);
                if (data.status === "failed") {
                    setScanError(data.error ?? "Failed to scan for orphans.");
                }
            }
        } catch (err) {
            setIsScanning(false);
            setScanError(err instanceof Error ? err.message : "Failed to scan for orphans.");
        }
    };

    const fetchMediaInfoStatus = React.useCallback(async () => {
        const response = await fetch("/api/mediaLibrary/debug/populate-media-info");
        if (!response.ok) {
            throw new Error(`Request failed with ${response.status}`);
        }
        const data = await response.json();
        setMediaInfoProgress(data);
        if (data.status === "running") {
            setIsPopulatingMediaInfo(true);
            return;
        }
        setIsPopulatingMediaInfo(false);
        if (data.status === "failed") {
            setMediaInfoPopulateError(data.error ?? "Failed to populate media info.");
        }
    }, []);

    const runMediaInfoPopulation = async () => {
        setIsPopulatingMediaInfo(true);
        setMediaInfoPopulateError(null);
        setMediaInfoProgress(null);
        try {
            const response = await fetch("/api/mediaLibrary/debug/populate-media-info", {
                method: "POST"
            });
            if (!response.ok) {
                throw new Error(`Request failed with ${response.status}`);
            }
            const data = await response.json();
            setMediaInfoProgress(data);
            if (data.status !== "running") {
                setIsPopulatingMediaInfo(false);
                if (data.status === "failed") {
                    setMediaInfoPopulateError(data.error ?? "Failed to populate media info.");
                }
            }
        } catch (err) {
            setIsPopulatingMediaInfo(false);
            setMediaInfoPopulateError(err instanceof Error ? err.message : "Failed to populate media info.");
        }
    };

    const runDefaultMediaInfoConversions = async () => {
        if (parsedMediaQuery.errors.length > 0) {
            setDefaultConvertError("Fix the media information query before converting.");
            return;
        }
        const total = mediaInfoResults?.length ?? 0;
        const confirmed = window.confirm(
            `Convert ${total} video${total === 1 ? "" : "s"} with default settings? ` +
            "Conversions will be scheduled for the 3:00am batch, and originals will be marked for deletion after conversion."
        );
        if (!confirmed) return;

        setIsDefaultConverting(true);
        setDefaultConvertError(null);
        setDefaultConvertStatus(null);
        try {
            const response = await fetch(
                `/api/mediaLibrary/media-information/conversions/default?${mediaInfoQueryString}`,
                {method: "POST"}
            );
            if (!response.ok) {
                throw new Error(`Request failed with ${response.status}`);
            }
            const data = await response.json() as ConversionBatchResult;
            const parts = [`Scheduled ${data.enqueued} of ${data.total} conversion${data.total === 1 ? "" : "s"} for the 3:00am batch.`];
            if (data.skipped > 0) {
                parts.push(`${data.skipped} already queued`);
            }
            if (data.missing > 0) {
                parts.push(`${data.missing} missing`);
            }
            setDefaultConvertStatus(parts.join(" "));
        } catch (err) {
            setDefaultConvertError(err instanceof Error ? err.message : "Failed to queue conversions.");
        } finally {
            setIsDefaultConverting(false);
        }
    };

    React.useEffect(() => {
        fetchOrphanScanStatus().catch(() => {
            // Ignore initial fetch errors; user action will surface errors.
        });
    }, [fetchOrphanScanStatus]);
    React.useEffect(() => {
        fetchMediaInfoStatus().catch(() => {
            // Ignore initial fetch errors; user action will surface errors.
        });
    }, [fetchMediaInfoStatus]);

    React.useEffect(() => {
        if (!isScanning) return;
        const intervalId = window.setInterval(() => {
            fetchOrphanScanStatus().catch(err => {
                setIsScanning(false);
                setScanError(err instanceof Error ? err.message : "Failed to scan for orphans.");
            });
        }, 1000);
        return () => window.clearInterval(intervalId);
    }, [fetchOrphanScanStatus, isScanning]);
    React.useEffect(() => {
        if (!isPopulatingMediaInfo) return;
        const intervalId = window.setInterval(() => {
            fetchMediaInfoStatus().catch(err => {
                setIsPopulatingMediaInfo(false);
                setMediaInfoPopulateError(err instanceof Error ? err.message : "Failed to populate media info.");
            });
        }, 1000);
        return () => window.clearInterval(intervalId);
    }, [fetchMediaInfoStatus, isPopulatingMediaInfo]);

    const requestMarkedDeletion = async () => {
        if (markedForDeletionCount === 0) {
            setDeleteStatus("No videos are marked for deletion.");
            return;
        }
        const confirmed = window.confirm(
            `Delete ${markedForDeletionCount} videos marked for deletion? This cannot be undone.`
        );
        if (!confirmed) return;

        setDeleteError(null);
        setDeleteStatus(null);
        setIsDeleting(true);
        try {
            const response = await fetch("/api/mediaLibrary/debug/delete-marked", {method: "POST"});
            if (!response.ok) {
                throw new Error(`Request failed with ${response.status}`);
            }
            const data = await response.json();
            const deleted = Number(data?.deleted ?? 0);
            const totalMarked = Number(data?.totalMarked ?? 0);
            const failed = Number(data?.failed ?? 0);
            const failedSuffix = failed > 0 ? ` (${failed} failed)` : "";
            setDeleteStatus(`Deleted ${deleted} of ${totalMarked} marked videos${failedSuffix}.`);
            await refresh();
        } catch (err) {
            setDeleteError(err instanceof Error ? err.message : "Failed to delete marked videos.");
        } finally {
            setIsDeleting(false);
        }
    };

    const requestShutdown = async () => {
        if (!config?.shutdownUrl) {
            setShutdownError("Shutdown URL not available.");
            return;
        }
        const confirmed = window.confirm("Shut down the application now?");
        if (!confirmed) return;

        setShutdownError(null);
        setShutdownStatus(null);
        setIsShuttingDown(true);
        try {
            const response = await fetch(config.shutdownUrl, {method: "POST"});
            if (!response.ok) {
                const fallbackResponse = await fetch(config.shutdownUrl);
                if (!fallbackResponse.ok) {
                    throw new Error(`Shutdown request failed with ${response.status}.`);
                }
            }
            setShutdownStatus("Shutdown requested. The server will stop shortly.");
        } catch (err) {
            setShutdownStatus("Shutdown requested. The server may already be stopping.");
        } finally {
            setIsShuttingDown(false);
        }
    };

    return <>
        <MainHeading>Admin</MainHeading>
        <SectionHeading>Maintenance</SectionHeading>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Files marked for deletion
            </div>
            <div className="text-4xl font-semibold mt-2">
                {isLoading ? "Loading..." : markedForDeletionCount}
            </div>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                Permanently delete videos and metadata that are flagged for removal.
            </p>
            <div className="mt-3">
                <StyledButton
                    onClick={requestMarkedDeletion}
                    disabled={isDeleting || isLoading || markedForDeletionCount === 0}
                    className="bg-red-500 hover:bg-red-600 dark:bg-red-600 dark:hover:bg-red-700"
                >
                    {isDeleting ? "Deleting..." : "Delete Marked Videos"}
                </StyledButton>
            </div>
            {deleteStatus && (
                <p className="mt-3 text-sm text-gray-700 dark:text-gray-200">
                    {deleteStatus}
                </p>
            )}
            {deleteError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    {deleteError}
                </p>
            )}
            {isError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    Failed to load deletion status.
                </p>
            )}
        </div>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Conversion queue
            </div>
            <div className="text-3xl font-semibold mt-2">
                {queuedConversionCount} queued
            </div>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                {runningConversionCount} running · {problematicConversionCount} failed
            </p>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                Clear queued and failed conversions. Running conversions will be cancelled.
            </p>
            <div className="mt-3">
                <StyledButton
                    onClick={clearConversionQueue}
                    disabled={isClearingConversions}
                    className="bg-amber-500 hover:bg-amber-600 dark:bg-amber-500 dark:hover:bg-amber-600"
                >
                    {isClearingConversions ? "Clearing..." : "Clear Conversion Queue"}
                </StyledButton>
            </div>
            {clearConversionsStatus && (
                <p className="mt-3 text-sm text-gray-700 dark:text-gray-200">
                    {clearConversionsStatus}
                </p>
            )}
            {clearConversionsError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    {clearConversionsError}
                </p>
            )}
        </div>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Disk space (media library)
            </div>
            <div className="text-3xl font-semibold mt-2">
                {isDiskLoading ? "Loading..." : formatBytes(usage?.usableBytes ?? 0)} usable
            </div>
            {!isDiskLoading && usage && (
                <div className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                    {formatBytes(usage?.freeBytes ?? 0)} free · {formatBytes(usage?.totalBytes ?? 0)} total
                </div>
            )}
            {isDiskError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    Failed to load disk usage.
                </p>
            )}
        </div>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Orphaned media cleanup
            </div>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                Scans tombstoned entries for leftover media files and removes any stray video or thumbnails. Hash files
                are preserved.
            </p>
            <div className="mt-3">
                <StyledButton onClick={runOrphanScan} disabled={isScanning}>
                    {isScanning ? "Scanning..." : "Scan for Orphans"}
                </StyledButton>
            </div>
            {scanProgress && scanProgress.status !== "idle" && (
                <div className="mt-4 text-sm text-gray-700 dark:text-gray-200">
                    <div
                        className="flex items-center justify-between text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                        <span>Progress</span>
                        <span>{scanProgressPercent}%</span>
                    </div>
                    <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                        <div
                            className="h-full bg-emerald-500 transition-[width] duration-300"
                            style={{
                                width: `${scanProgressPercent}%`
                            }}
                        />
                    </div>
                    <div className="mt-3 grid gap-y-1 text-sm">
                        <div>
                            Status: {scanProgress.status === "completed"
                            ? `completed at ${formatTimestamp(scanProgress.finishedAt)}`
                            : scanProgress.status}
                        </div>
                        <div>
                            Processed: {scanProgress.processed} / {scanProgress.totalTombstones}
                        </div>
                        <div>Orphans found: {scanProgress.orphansFound}</div>
                        <div>Orphans deleted: {scanProgress.orphansDeleted}</div>
                        <div>Failed deletions: {scanProgress.failedDeletions}</div>
                        <div>Space freed: {formatBytes(scanProgress.bytesFreed)}</div>
                    </div>
                </div>
            )}
            {scanError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    {scanError}
                </p>
            )}
        </div>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Shutdown
            </div>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                Gracefully stop the server. Active downloads and tasks will be interrupted.
            </p>
            <div className="mt-3">
                <StyledButton
                    onClick={requestShutdown}
                    disabled={isShuttingDown || isConfigLoading}
                    className="bg-red-500 hover:bg-red-600 dark:bg-red-600 dark:hover:bg-red-700"
                >
                    {isShuttingDown ? "Shutting down..." : "Shut Down Application"}
                </StyledButton>
            </div>
            {shutdownStatus && (
                <p className="mt-3 text-sm text-gray-700 dark:text-gray-200">
                    {shutdownStatus}
                </p>
            )}
            {(shutdownError || isConfigError) && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    {shutdownError ?? "Failed to load shutdown configuration."}
                </p>
            )}
        </div>
        <SectionHeading>Media Insights</SectionHeading>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Populate media info
            </div>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                Fill missing media metadata in the database without re-probing existing entries.
            </p>
            <div className="mt-3">
                <StyledButton onClick={runMediaInfoPopulation} disabled={isPopulatingMediaInfo}>
                    {isPopulatingMediaInfo ? "Populating..." : "Populate Media Info"}
                </StyledButton>
            </div>
            {mediaInfoProgress && mediaInfoProgress.status !== "idle" && (
                <div className="mt-4 text-sm text-gray-700 dark:text-gray-200">
                    <div
                        className="flex items-center justify-between text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                        <span>Progress</span>
                        <span>{mediaInfoProgressPercent}%</span>
                    </div>
                    <div className="mt-2 h-2 w-full overflow-hidden rounded-full bg-gray-200 dark:bg-gray-700">
                        <div
                            className="h-full bg-emerald-500 transition-[width] duration-300"
                            style={{
                                width: `${mediaInfoProgressPercent}%`
                            }}
                        />
                    </div>
                    <div className="mt-3 grid gap-y-1 text-sm">
                        <div>
                            Status: {mediaInfoProgress.status === "completed"
                            ? `completed at ${formatTimestamp(mediaInfoProgress.finishedAt)}`
                            : mediaInfoProgress.status}
                        </div>
                        <div>
                            Processed: {mediaInfoProgress.processed} / {mediaInfoProgress.totalEntries}
                        </div>
                        <div>Probed: {mediaInfoProgress.probed}</div>
                        <div>Skipped: {mediaInfoProgress.skipped}</div>
                        <div>Succeeded: {mediaInfoProgress.succeeded}</div>
                        <div>Failed: {mediaInfoProgress.failed}</div>
                    </div>
                </div>
            )}
            {mediaInfoPopulateError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    {mediaInfoPopulateError}
                </p>
            )}
        </div>
        <div className={`${commonStyles.cardContainer} ${commonStyles.standardMargin}`}>
            <div className="text-sm uppercase tracking-wide text-gray-500 dark:text-gray-400">
                Media information query
            </div>
            <p className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                Query stored media information (no re-probing). Example:{" "}
                <span className={commonStyles.monospace}>{DEFAULT_MEDIA_QUERY}</span>
            </p>
            <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
                Sort fields: <span className={commonStyles.monospace}>{MEDIA_QUERY_FIELDS}</span>
            </p>
            <p className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                Exclude terms with <span className={commonStyles.monospace}>-term</span> (matches title substring).
            </p>
            <div className="mt-3">
                <input
                    value={mediaQuery}
                    onChange={(event) => setMediaQuery(event.target.value)}
                    className="w-full rounded-lg border border-gray-300 dark:border-gray-700 bg-white dark:bg-gray-900 px-3 py-2 text-sm focus:outline-none focus:ring-2 focus:ring-emerald-500"
                    placeholder={DEFAULT_MEDIA_QUERY}
                />
            </div>
            {parsedMediaQuery.errors.length > 0 && (
                <div className={`${commonStyles.errorText} mt-2 text-sm`}>
                    {parsedMediaQuery.errors.join(" · ")}
                </div>
            )}
            <div className="mt-4 flex flex-wrap items-center gap-3">
                <StyledButton
                    onClick={runDefaultMediaInfoConversions}
                    disabled={isDefaultConverting || isMediaInfoLoading || parsedMediaQuery.errors.length > 0}
                >
                    {isDefaultConverting ? "Queueing..." : "Convert all (default)"}
                </StyledButton>
                <span className="text-xs text-gray-500 dark:text-gray-400">
                    {isMediaInfoLoading
                        ? "Loading matches..."
                        : `Matches ${mediaInfoResults?.length ?? 0} entr${(mediaInfoResults?.length ?? 0) === 1 ? "y" : "ies"}.`}
                </span>
            </div>
            {defaultConvertStatus && (
                <p className="mt-2 text-sm text-gray-700 dark:text-gray-200">
                    {defaultConvertStatus}
                </p>
            )}
            {defaultConvertError && (
                <p className={`${commonStyles.errorText} mt-2 text-sm`}>
                    {defaultConvertError}
                </p>
            )}
            <div className="mt-4 overflow-x-auto">
                <table className="w-full min-w-[760px] text-sm">
                    <thead>
                    <tr className="border-b border-gray-200 dark:border-gray-700 text-left text-xs uppercase tracking-wide text-gray-500 dark:text-gray-400">
                        <th className="py-2 pr-3">Title</th>
                        <th className="py-2 pr-3">Resolution</th>
                        <th className="py-2 pr-3">Frame rate</th>
                        <th className="py-2 pr-3">Bitrate</th>
                        <th className="py-2 pr-3">File size</th>
                        <th className="py-2 pr-3">Duration</th>
                        <th className="py-2 pr-3">Conversion</th>
                    </tr>
                    </thead>
                    <tbody>
                    {mediaInfoResults?.map((row) => {
                        const conversion = conversionBySource.get(row.id);
                        const isScheduled = conversion?.mode === "SCHEDULED";
                        const conversionLabel = conversion
                            ? isScheduled
                                ? "Scheduled"
                                : "Queued"
                            : null;
                        return (
                            <tr key={row.id} className="border-b border-gray-100 dark:border-gray-700/60">
                                <td className="py-2 pr-3">
                                    <Link
                                        to={`/movie/${row.id}`}
                                        className="text-emerald-600 dark:text-emerald-400 hover:underline"
                                    >
                                        {row.title}
                                    </Link>
                                    <div className="text-xs text-gray-500 dark:text-gray-400 truncate">
                                        {row.originUrl}
                                    </div>
                                </td>
                                <td className="py-2 pr-3">{formatResolution(row.width, row.height)}</td>
                                <td className="py-2 pr-3">{formatFrameRate(row.frameRate)}</td>
                                <td className="py-2 pr-3">{formatBitRate(row.bitRate)}</td>
                                <td className="py-2 pr-3">{formatOptionalBytes(row.fileSize)}</td>
                                <td className="py-2 pr-3">{formatDurationSeconds(row.durationSeconds)}</td>
                                <td className="py-2 pr-3">
                                    {conversionLabel ? (
                                        <span
                                            className="inline-flex items-center gap-1 rounded-full bg-slate-900/10 px-2 py-1 text-xs text-slate-700 dark:bg-slate-200/10 dark:text-slate-100"
                                            title={`${conversionLabel} for conversion`}
                                        >
                                            <ArrowsRightLeftIcon className="h-3.5 w-3.5"/>
                                            {conversionLabel}
                                        </span>
                                    ) : (
                                        <span className="text-xs text-gray-400 dark:text-gray-500">—</span>
                                    )}
                                </td>
                            </tr>
                        );
                    })}
                    {isMediaInfoLoading && (
                        <tr>
                            <td className="py-4 text-sm text-gray-500 dark:text-gray-400" colSpan={7}>
                                Loading media information...
                            </td>
                        </tr>
                    )}
                    {mediaInfoResults && mediaInfoResults.length === 0 && (
                        <tr>
                            <td className="py-4 text-sm text-gray-500 dark:text-gray-400" colSpan={7}>
                                No stored media information matches this query yet.
                            </td>
                        </tr>
                    )}
                    </tbody>
                </table>
            </div>
            {mediaInfoError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    Failed to load media information results.
                </p>
            )}
        </div>
    </>;
}
