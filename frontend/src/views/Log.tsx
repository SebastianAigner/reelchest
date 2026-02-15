import * as React from "react";
import {useAbortableSWR} from "../hooks/useAbortableSWR";
import {fetcher} from "../utils";
import {MainHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import LogEntry = IoSebi.LogEntry;

function useLog() {
    const {data, error} = useAbortableSWR<Array<LogEntry>>(`/api/log`, fetcher, {refreshInterval: 1000})
    return {
        log: data,
        isLoading: !error && !data,
        isError: error
    }
}


export function Log() {
    const {log, isLoading, isError} = useLog()
    return <>
        <MainHeading>Logs</MainHeading>
        <div className="border border-gray-200 dark:border-gray-700 rounded-lg bg-gray-50 dark:bg-gray-800/40">
            {isLoading && <p className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">Loading logs…</p>}
            {isError && <p className={`px-4 py-3 text-sm ${commonStyles.errorText}`}>Failed to load logs.</p>}
            {!isLoading && !isError && log?.length === 0 && (
                <p className="px-4 py-3 text-sm text-gray-500 dark:text-gray-400">No log entries yet.</p>
            )}
            <ul className={`${commonStyles.monospace} text-xs leading-5 divide-y divide-gray-200 dark:divide-gray-700 max-h-[70vh] overflow-y-auto px-4 py-2`}>
                {
                    log?.map((item, index) =>
                        <li key={`${item.formattedMessage}-${index}`} className="py-1">
                            <p className={commonStyles.preWrap}>{item.formattedMessage}</p>
                        </li>
                    )
                }
            </ul>
        </div>
        <div className={commonStyles.fadeOut}/>
    </>;
}
