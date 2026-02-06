import * as React from "react";
import {useAbortableSWR} from "../hooks/useAbortableSWR";
import {fetcher} from "../utils";
import {MainHeading, SectionHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import {StyledButton} from "../components/StyledButton";

function useMediaLibraryEntries() {
    const {data, error} = useAbortableSWR<Array<AutoTaggedMediaLibraryEntry>>(
        "/api/mediaLibrary?auto=true",
        fetcher
    );

    return {
        entries: data || [],
        isLoading: !data && !error,
        isError: error
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

export function Admin() {
    const {entries, isLoading, isError} = useMediaLibraryEntries();
    const {usage, isLoading: isDiskLoading, isError: isDiskError} = useDiskUsage();
    const {config, isLoading: isConfigLoading, isError: isConfigError} = useApplicationConfig();
    const markedForDeletionCount = React.useMemo(() => {
        return entries.filter(entry => entry.mediaLibraryEntry.markedForDeletion).length;
    }, [entries]);
    const [scanProgress, setScanProgress] = React.useState<OrphanScanProgress | null>(null);
    const [scanError, setScanError] = React.useState<string | null>(null);
    const [isScanning, setIsScanning] = React.useState(false);
    const [shutdownError, setShutdownError] = React.useState<string | null>(null);
    const [shutdownStatus, setShutdownStatus] = React.useState<string | null>(null);
    const [isShuttingDown, setIsShuttingDown] = React.useState(false);
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

    React.useEffect(() => {
        fetchOrphanScanStatus().catch(() => {
            // Ignore initial fetch errors; user action will surface errors.
        });
    }, [fetchOrphanScanStatus]);

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
            {isError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    Failed to load deletion status.
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
                        <div>Status: {scanProgress.status}</div>
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
    </>;
}
