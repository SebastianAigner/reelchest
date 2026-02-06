import * as React from "react";
import useSWR from "swr";
import {fetcher} from "../utils";
import {MainHeading, SectionHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import {AutoTaggedMediaLibraryEntry} from "../models/AutoTaggedMediaLibraryEntry";
import {StyledButton} from "../components/StyledButton";

function useMediaLibraryEntries() {
    const {data, error} = useSWR<Array<AutoTaggedMediaLibraryEntry>>(
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
    const {data, error} = useSWR<{
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

type OrphanScanResult = {
    orphansFound: number;
    orphansDeleted: number;
    failedDeletions: number;
    bytesFreed: number;
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
    const markedForDeletionCount = React.useMemo(() => {
        return entries.filter(entry => entry.mediaLibraryEntry.markedForDeletion).length;
    }, [entries]);
    const [scanResult, setScanResult] = React.useState<OrphanScanResult | null>(null);
    const [scanError, setScanError] = React.useState<string | null>(null);
    const [isScanning, setIsScanning] = React.useState(false);

    const runOrphanScan = async () => {
        setIsScanning(true);
        setScanError(null);
        setScanResult(null);
        try {
            const response = await fetch("/api/mediaLibrary/debug/scan-orphans", {
                method: "POST"
            });
            if (!response.ok) {
                throw new Error(`Request failed with ${response.status}`);
            }
            const data = await response.json();
            setScanResult(data);
        } catch (err) {
            setScanError(err instanceof Error ? err.message : "Failed to scan for orphans.");
        } finally {
            setIsScanning(false);
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
                {isDiskLoading ? "Loading..." : formatBytes(usage?.freeBytes ?? 0)} free
            </div>
            {!isDiskLoading && usage && (
                <div className="mt-2 text-sm text-gray-600 dark:text-gray-300">
                    {formatBytes(usage?.usableBytes ?? 0)} usable · {formatBytes(usage?.totalBytes ?? 0)} total
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
            {scanResult && (
                <div className="mt-4 text-sm text-gray-700 dark:text-gray-200">
                    <div>Orphans found: {scanResult.orphansFound}</div>
                    <div>Orphans deleted: {scanResult.orphansDeleted}</div>
                    <div>Failed deletions: {scanResult.failedDeletions}</div>
                    <div>Space freed: {formatBytes(scanResult.bytesFreed)}</div>
                </div>
            )}
            {scanError && (
                <p className={`${commonStyles.errorText} mt-3`}>
                    {scanError}
                </p>
            )}
        </div>
    </>;
}
