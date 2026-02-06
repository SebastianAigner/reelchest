import * as React from "react";
import {useAbortableSWR} from "../hooks/useAbortableSWR";
import {fetcher} from "../utils";
import axios from "axios";
import {MainHeading, SectionHeading} from "../components/Typography";
import {commonStyles} from "../styles/common";
import {useTheme} from "../context/ThemeContext";
import MetadatedDownloadQueueEntry = IoSebi.MetadatedDownloadQueueEntry;
import ProblematicTaskDTO = IoSebi.ProblematicTaskDTO;

function useQueue() {
    const {data, error} = useAbortableSWR<Array<MetadatedDownloadQueueEntry>>("/api/queue", fetcher, {refreshInterval: 1000})
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
    } = useAbortableSWR<Array<ProblematicTaskDTO>>("/api/problematic", fetcher, {refreshInterval: 1000})
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
    const {theme} = useTheme()
    const isDark = theme === "dark"
    if (isLoading) return <SectionHeading>Loading Downloads...</SectionHeading>
    const queueItems = queue?.slice().reverse() ?? []
    const queueStats = {
        total: queueItems.length,
        queued: queueItems.filter(item => item.queueEntry.progress === 0).length,
        active: queueItems.filter(item => item.queueEntry.progress > 0 && item.queueEntry.progress < 1).length,
        complete: queueItems.filter(item => item.queueEntry.progress >= 1).length
    }
    const queueSurfaceStyle: React.CSSProperties = {
        backgroundImage: isDark
            ? "radial-gradient(circle at 10% 15%, rgba(56, 189, 248, 0.18), transparent 50%), radial-gradient(circle at 90% 10%, rgba(34, 197, 94, 0.16), transparent 45%), linear-gradient(140deg, rgba(15, 23, 42, 0.98), rgba(17, 24, 39, 0.96))"
            : "radial-gradient(circle at 8% 15%, rgba(255, 214, 170, 0.35), transparent 50%), radial-gradient(circle at 88% 10%, rgba(160, 210, 255, 0.35), transparent 45%), linear-gradient(140deg, rgba(255, 255, 255, 0.98), rgba(244, 248, 255, 0.96))",
        border: isDark ? "1px solid rgba(148, 163, 184, 0.18)" : "1px solid rgba(15, 23, 42, 0.08)",
        borderRadius: "26px",
        padding: "24px",
        boxShadow: isDark ? "0 20px 50px rgba(0, 0, 0, 0.45)" : "0 20px 50px rgba(15, 23, 42, 0.12)",
        position: "relative",
        overflow: "hidden",
        fontFamily: '"Space Grotesk", "Sora", "IBM Plex Sans", "Noto Sans", sans-serif',
        fontKerning: "normal",
        fontFeatureSettings: '"kern", "liga"',
        letterSpacing: "0.01em"
    }
    const queueHeaderStyle: React.CSSProperties = {
        display: "flex",
        alignItems: "flex-start",
        justifyContent: "space-between",
        gap: "16px",
        flexWrap: "wrap",
        marginBottom: "18px"
    }
    const queueStatPillStyle: React.CSSProperties = {
        display: "flex",
        alignItems: "center",
        gap: "6px",
        padding: "6px 10px",
        borderRadius: "999px",
        background: isDark ? "rgba(148, 163, 184, 0.16)" : "rgba(15, 23, 42, 0.06)",
        color: isDark ? "rgba(226, 232, 240, 0.9)" : "rgba(15, 23, 42, 0.85)",
        fontSize: "0.85em",
        fontWeight: 600
    }
    const queueListStyle: React.CSSProperties = {
        listStyle: "none",
        margin: 0,
        padding: 0,
        display: "grid",
        gap: "16px"
    }
    const queueCardStyle: React.CSSProperties = {
        background: isDark ? "rgba(15, 23, 42, 0.72)" : "rgba(255, 255, 255, 0.88)",
        border: isDark ? "1px solid rgba(148, 163, 184, 0.14)" : "1px solid rgba(15, 23, 42, 0.08)",
        borderRadius: "20px",
        padding: "18px 20px",
        boxShadow: isDark ? "0 12px 30px rgba(0, 0, 0, 0.35)" : "0 12px 30px rgba(15, 23, 42, 0.08)",
        display: "grid",
        gap: "12px"
    }
    const progressTrackStyle: React.CSSProperties = {
        height: "10px",
        borderRadius: "999px",
        background: isDark ? "rgba(148, 163, 184, 0.18)" : "rgba(15, 23, 42, 0.08)",
        overflow: "hidden",
        flex: 1,
        minWidth: "120px"
    }
    return <>
        <MainHeading>Downloads</MainHeading>
        <section style={queueSurfaceStyle}>
            <div style={queueHeaderStyle}>
                <div>
                    <div style={{
                        fontSize: "1.4em",
                        fontWeight: 700,
                        color: isDark ? "rgba(226, 232, 240, 0.95)" : "rgba(15, 23, 42, 0.9)",
                        marginBottom: "6px"
                    }}>
                        Downloads Queue
                    </div>
                </div>
                <div style={{display: "flex", gap: "8px", flexWrap: "wrap"}}>
                    <div style={queueStatPillStyle}>Total {queueStats.total}</div>
                    <div style={{
                        ...queueStatPillStyle,
                        background: isDark ? "rgba(251, 191, 36, 0.18)" : "rgba(255, 199, 147, 0.35)",
                        color: isDark ? "rgba(252, 211, 77, 0.9)" : queueStatPillStyle.color
                    }}>
                        Waiting {queueStats.queued}
                    </div>
                    <div style={{
                        ...queueStatPillStyle,
                        background: isDark ? "rgba(56, 189, 248, 0.18)" : "rgba(125, 211, 252, 0.35)",
                        color: isDark ? "rgba(125, 211, 252, 0.95)" : queueStatPillStyle.color
                    }}>
                        Active {queueStats.active}
                    </div>
                    <div style={{
                        ...queueStatPillStyle,
                        background: isDark ? "rgba(52, 211, 153, 0.18)" : "rgba(110, 231, 183, 0.35)",
                        color: isDark ? "rgba(110, 231, 183, 0.95)" : queueStatPillStyle.color
                    }}>
                        Complete {queueStats.complete}
                    </div>
                </div>
            </div>
            {isError && (
                <div style={{
                    padding: "10px 12px",
                    borderRadius: "12px",
                    border: isDark ? "1px solid rgba(248, 113, 113, 0.35)" : "1px solid rgba(239, 68, 68, 0.2)",
                    background: isDark ? "rgba(127, 29, 29, 0.45)" : "rgba(254, 226, 226, 0.6)",
                    color: isDark ? "rgba(254, 226, 226, 0.95)" : "rgba(185, 28, 28, 0.9)",
                    marginBottom: "12px"
                }}>
                    Could not load the downloads queue right now.
                </div>
            )}
            {queueItems.length === 0 ? (
                <div style={{
                    padding: "18px",
                    borderRadius: "18px",
                    border: isDark ? "1px dashed rgba(148, 163, 184, 0.25)" : "1px dashed rgba(15, 23, 42, 0.12)",
                    background: isDark ? "rgba(15, 23, 42, 0.55)" : "rgba(255, 255, 255, 0.6)",
                    color: isDark ? "rgba(226, 232, 240, 0.7)" : "rgba(15, 23, 42, 0.6)",
                    fontSize: "0.95em",
                    textAlign: "center"
                }}>
                    Nothing is queued yet. Add a new download and it will show up here.
                </div>
            ) : (
                <ul style={queueListStyle}>
                    {queueItems.map((item, index) => {
                        const progress = Math.min(1, Math.max(0, item.queueEntry.progress))
                        const progressPercent = Math.round(progress * 100)
                        const statusLabel = progressPercent === 0 ? "Queued" : progressPercent >= 100 ? "Finishing" : "Downloading"
                        const statusStyle: React.CSSProperties = progressPercent >= 100
                            ? {
                                background: isDark ? "rgba(52, 211, 153, 0.18)" : "rgba(110, 231, 183, 0.35)",
                                color: isDark ? "rgba(110, 231, 183, 0.95)" : "rgba(4, 120, 87, 0.9)"
                            }
                            : progressPercent === 0
                                ? {
                                    background: isDark ? "rgba(251, 191, 36, 0.18)" : "rgba(255, 199, 147, 0.35)",
                                    color: isDark ? "rgba(252, 211, 77, 0.95)" : "rgba(180, 83, 9, 0.9)"
                                }
                                : {
                                    background: isDark ? "rgba(56, 189, 248, 0.18)" : "rgba(125, 211, 252, 0.35)",
                                    color: isDark ? "rgba(125, 211, 252, 0.95)" : "rgba(3, 105, 161, 0.9)"
                                }
                        const progressFillStyle: React.CSSProperties = {
                            height: "100%",
                            width: `${progressPercent}%`,
                            borderRadius: "999px",
                            backgroundImage: progressPercent >= 100
                                ? "linear-gradient(90deg, #6ee7b7, #34d399)"
                                : progressPercent === 0
                                    ? "linear-gradient(90deg, #fcd34d, #fdba74)"
                                    : "linear-gradient(90deg, #7dd3fc, #38bdf8, #0ea5e9)",
                            transition: "width 0.3s ease"
                        }

                        return (
                            <li key={item.queueEntry.originUrl} style={queueCardStyle}>
                                <div style={{display: "flex", alignItems: "flex-start", gap: "12px"}}>
                                    <div style={{
                                        width: "40px",
                                        height: "40px",
                                        borderRadius: "14px",
                                        background: isDark ? "rgba(148, 163, 184, 0.18)" : "rgba(15, 23, 42, 0.06)",
                                        display: "flex",
                                        alignItems: "center",
                                        justifyContent: "center",
                                        fontWeight: 700,
                                        color: isDark ? "rgba(226, 232, 240, 0.8)" : "rgba(15, 23, 42, 0.65)"
                                    }}>
                                        {index + 1}
                                    </div>
                                    <div style={{flex: 1, minWidth: 0}}>
                                        <div style={{
                                            fontWeight: 700,
                                            fontSize: "1.05em",
                                            color: isDark ? "rgba(226, 232, 240, 0.95)" : "rgba(15, 23, 42, 0.9)",
                                            marginBottom: "6px"
                                        }}>
                                            {item.title || "Untitled"}
                                        </div>
                                        <div style={{
                                            color: isDark ? "rgba(226, 232, 240, 0.6)" : "rgba(15, 23, 42, 0.55)",
                                            fontSize: "0.85em",
                                            wordBreak: "break-all"
                                        }}>
                                            {item.queueEntry.originUrl}
                                        </div>
                                    </div>
                                    <div style={{
                                        padding: "6px 12px",
                                        borderRadius: "999px",
                                        fontSize: "0.8em",
                                        fontWeight: 600,
                                        whiteSpace: "nowrap",
                                        ...statusStyle
                                    }}>
                                        {statusLabel}
                                    </div>
                                </div>
                                <div style={{display: "flex", alignItems: "center", gap: "12px"}}>
                                    <div
                                        role="progressbar"
                                        aria-valuemin={0}
                                        aria-valuemax={100}
                                        aria-valuenow={progressPercent}
                                        style={progressTrackStyle}
                                    >
                                        <div style={progressFillStyle}/>
                                    </div>
                                    <div style={{
                                        minWidth: "52px",
                                        textAlign: "right",
                                        fontWeight: 700,
                                        color: isDark ? "rgba(226, 232, 240, 0.85)" : "rgba(15, 23, 42, 0.8)"
                                    }}>
                                        {progressPercent}%
                                    </div>
                                </div>
                            </li>
                        )
                    })}
                </ul>
            )}
        </section>
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
