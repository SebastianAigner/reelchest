import * as React from "react";
import useSWR from "swr/esm";
import {fetcher} from "../utils";
import axios from "axios";
import {MainHeading, SectionHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import MetadatedDownloadQueueEntry = IoSebi.MetadatedDownloadQueueEntry;
import ProblematicTaskDTO = IoSebi.ProblematicTaskDTO;

function useQueue() {
    const {data, error} = useSWR<Array<MetadatedDownloadQueueEntry>>("/api/queue", fetcher, {refreshInterval: 1000})
    return {
        queue: data,
        isLoading: !error && !data,
        isError: error
    }
}

function useProblematic() {
    const {
        data,
        error
    } = useSWR<Array<ProblematicTaskDTO>>("/api/problematic", fetcher, {refreshInterval: 1000})
    return {
        queue: data,
        isLoading: !error && !data,
        isError: error
    }
}

export function Downloads() {
    const {queue, isLoading, isError} = useQueue()
    const {queue: probQ, isLoading: probLoading, isError: probErr} = useProblematic()
    const [visibleErrors, setVisibleErrors] = React.useState<Set<string>>(new Set())
    if (isLoading) return <SectionHeading>Loading Downloads...</SectionHeading>
    return <>
        <MainHeading>Downloads</MainHeading>
        <ul>
            {
                queue?.map(item =>
                    <li key={item.queueEntry.originUrl}>
                        {item.queueEntry.progress != 0 && <progress value={item.queueEntry.progress}/>}
                        <p>
                            {item.title}: {Number(item.queueEntry.progress * 100).toFixed(2)}%
                        </p>
                    </li>
                )
            }
        </ul>
        <SectionHeading>Problematic Entries (<a href={"/api/problematic"}>endpoint</a>)</SectionHeading>
        <ul>
            {
                probQ?.map(item =>
                    <li key={item.originUrl} style={{
                        border: '1px solid var(--border-color, #ddd)',
                        borderRadius: '8px',
                        padding: '16px',
                        margin: '12px 0',
                        backgroundColor: 'var(--background-color, rgba(0, 0, 0, 0.02))'
                    }}>
                        <div style={{
                            marginBottom: '12px'
                        }}>
                            <a href={item.originUrl} style={{
                                wordBreak: 'break-all'
                            }}>
                                {item.originUrl}
                            </a>
                        </div>
                        <div style={{
                            display: 'flex',
                            gap: '8px',
                            marginBottom: '12px',
                            flexWrap: 'wrap',
                            alignItems: 'center',
                            width: '100%',
                            minWidth: 0
                        }}>
                            <div style={{
                                display: 'flex',
                                gap: '8px',
                                alignItems: 'flex-start',
                                flexWrap: 'wrap',
                                width: '100%',
                                minWidth: 0
                            }}>
                                <div
                                    onClick={() => {
                                        const newVisibleErrors = new Set(visibleErrors);
                                        if (visibleErrors.has(item.originUrl)) {
                                            newVisibleErrors.delete(item.originUrl);
                                        } else {
                                            newVisibleErrors.add(item.originUrl);
                                        }
                                        setVisibleErrors(newVisibleErrors);
                                    }}
                                    style={{
                                        fontFamily: '"JetBrains Mono", "Fira Code", "Consolas", monospace',
                                        fontSize: '0.9em',
                                        padding: '8px 10px',
                                        backgroundColor: 'var(--error-background, rgba(220, 0, 0, 0.05))',
                                        border: '1px solid var(--error-border, rgba(220, 0, 0, 0.1))',
                                        borderRadius: '4px',
                                        color: 'var(--error-text, #d32f2f)',
                                        lineHeight: '1.4',
                                        cursor: 'pointer',
                                        transition: 'all 0.2s ease',
                                        position: 'relative',
                                        paddingRight: '30px',
                                        flex: '1 1 auto',
                                        minWidth: 0,
                                        overflow: 'hidden',
                                        textOverflow: 'ellipsis',
                                        whiteSpace: 'nowrap'
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.backgroundColor = 'var(--error-background-hover, rgba(220, 0, 0, 0.08))';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.backgroundColor = 'var(--error-background, rgba(220, 0, 0, 0.05))';
                                    }}>
                                    {item.error.split('\n')[0]}
                                    <span style={{
                                        position: 'absolute',
                                        right: '10px',
                                        top: '50%',
                                        transform: `translateY(-50%) rotate(${visibleErrors.has(item.originUrl) ? '0deg' : '-90deg'})`,
                                        transition: 'transform 0.2s ease',
                                        opacity: 0.6,
                                        fontSize: '0.85em'
                                    }}>▼</span>
                                </div>
                                <button
                                    onClick={() => {
                                        return axios.post("/api/problematic/retry", {
                                            url: item.originUrl
                                        })
                                    }}
                                    style={{
                                        fontFamily: 'inherit',
                                        fontSize: '0.85em',
                                        padding: '6px 12px',
                                        border: '1px solid var(--action-border, rgba(25, 118, 210, 0.2))',
                                        borderRadius: '4px',
                                        backgroundColor: 'var(--action-background, rgba(25, 118, 210, 0.04))',
                                        color: 'var(--action-text, #1976d2)',
                                        cursor: 'pointer',
                                        transition: 'all 0.2s ease',
                                        display: 'inline-flex',
                                        alignItems: 'center',
                                        gap: '6px'
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.backgroundColor = 'var(--action-background-hover, rgba(25, 118, 210, 0.08))';
                                        e.currentTarget.style.borderColor = 'var(--action-border-hover, rgba(25, 118, 210, 0.3))';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.backgroundColor = 'var(--action-background, rgba(25, 118, 210, 0.04))';
                                        e.currentTarget.style.borderColor = 'var(--action-border, rgba(25, 118, 210, 0.2))';
                                    }}>
                                    <span style={{fontSize: '1.1em'}}>↺</span>
                                    Retry
                                </button>
                                <button
                                    onClick={() => {
                                        return axios.post("/api/problematic/remove", {
                                            url: item.originUrl
                                        })
                                    }}
                                    style={{
                                        fontFamily: 'inherit',
                                        fontSize: '0.85em',
                                        padding: '6px 12px',
                                        border: '1px solid var(--danger-border, rgba(211, 47, 47, 0.2))',
                                        borderRadius: '4px',
                                        backgroundColor: 'var(--danger-background, rgba(211, 47, 47, 0.04))',
                                        color: 'var(--danger-text, #d32f2f)',
                                        cursor: 'pointer',
                                        transition: 'all 0.2s ease',
                                        display: 'inline-flex',
                                        alignItems: 'center',
                                        gap: '6px'
                                    }}
                                    onMouseEnter={(e) => {
                                        e.currentTarget.style.backgroundColor = 'var(--danger-background-hover, rgba(211, 47, 47, 0.08))';
                                        e.currentTarget.style.borderColor = 'var(--danger-border-hover, rgba(211, 47, 47, 0.3))';
                                    }}
                                    onMouseLeave={(e) => {
                                        e.currentTarget.style.backgroundColor = 'var(--danger-background, rgba(211, 47, 47, 0.04))';
                                        e.currentTarget.style.borderColor = 'var(--danger-border, rgba(211, 47, 47, 0.2))';
                                    }}>
                                    <span style={{fontSize: '1.1em'}}>×</span>
                                    Delete
                                </button>
                            </div>
                        </div>
                        {visibleErrors.has(item.originUrl) && (
                            <pre style={{
                                fontFamily: '"JetBrains Mono", "Fira Code", "Consolas", monospace',
                                whiteSpace: 'pre',
                                backgroundColor: 'var(--code-background, rgba(0, 0, 0, 0.05))',
                                color: 'var(--code-color, inherit)',
                                padding: '12px',
                                borderRadius: '6px',
                                margin: '0',
                                fontSize: '0.9em',
                                lineHeight: '1.5',
                                boxShadow: 'inset 0 1px 3px var(--shadow-color, rgba(0, 0, 0, 0.1))',
                                maxHeight: '400px',
                                overflowY: 'auto',
                                overflowX: 'auto',
                                border: '1px solid var(--border-color, rgba(0, 0, 0, 0.1))'
                            }}>
                                {item.error.split('\n').slice(1).join('\n')}
                            </pre>
                        )}
                    </li>
                )
            }
        </ul>
        <div className={commonStyles.fadeOut}/>
    </>;
}
