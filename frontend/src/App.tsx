import * as React from "react";

import {useAbortableSWR} from "./hooks/useAbortableSWR";
import {HashRouter, Link, Route, Switch} from "react-router-dom";
import {ThumbnailDebug} from "./views/ThumbnailDebug";
import {MediaLibrary} from "./views/MediaLibrary";
import {Search} from "./views/Search";
import {Add} from "./views/Add";
import {Downloads} from "./views/Downloads";
import {Log} from "./views/Log";
import {Movie} from "./views/Movie";
import {ConfigurationManager} from "./views/ConfigurationManager";
import {DuplicatesManager} from "./views/DuplicatesManager";
import {SubtitleKitchen} from "./views/SubtitleKitchen";
import {Upload} from "./views/Upload";
import {Admin} from "./views/Admin";
import {Convert} from "./views/Convert";
import {NavGrid, VStack} from "./components/Layout";
import {ThemeProvider, useTheme} from "./context/ThemeContext";
import {commonStyles} from "./styles/common";
import {PlaylistProvider} from "./context/PlaylistContext";
import {fetcher} from "./utils";

// Navigation bar supports conditional rendering by setting showNav to false.
// However, this is part of the actual location, not the react hash router emulated string. So:
// http://0.0.0.0:8080/?showNav=false#/movie/asdf?showPlayer=false

const NavigationBar: React.FC = () => {
    const queryParams = new URLSearchParams(location.search);
    const showNav = queryParams.get("showNav") != "false";
    const {theme, toggleTheme} = useTheme();

    return <NavGrid className={"text-center"}>
        {showNav && (
            <>
                <div>
                    <Link to="/search" className={commonStyles.navLink}>Search</Link>
                </div>
                <div>
                    <Link to="/mediaLibrary" className={commonStyles.navLink}>
                        <span className="inline-flex items-center gap-2">
                            Media Library
                        </span>
                    </Link>
                </div>
                <div>
                    <Link to="/downloads" className={commonStyles.navLink}>Downloads</Link>
                </div>
                <div>
                    <Link to="/duplicates" className={commonStyles.navLink}>Duplicates</Link>
                </div>
                <div>
                    <Link to="/tags" className={commonStyles.navLink}>Tags</Link>
                </div>
                <div>
                    <Link to="/queries" className={commonStyles.navLink}>Queries</Link>
                </div>
                <div>
                    <Link to="/log" className={commonStyles.navLink}>Log</Link>
                </div>
                <div>
                    <Link to={"/subtitleKitchen"} className={commonStyles.navLink}>Subtitle Kitchen</Link>
                </div>
                <div>
                    <Link to={"/upload"} className={commonStyles.navLink}>Upload</Link>
                </div>
                <div>
                    <Link to={"/debug/thumbnails"} className={commonStyles.navLink}>Debug Thumbnails</Link>
                </div>
                <div>
                    <Link to={"/admin"} className={commonStyles.navLink}>Admin</Link>
                </div>
                <div>
                    <button
                        onClick={toggleTheme}
                        className="px-4 py-2 rounded-lg bg-gray-200 dark:bg-gray-700 hover:bg-gray-300 dark:hover:bg-gray-600 transition-colors"
                        aria-label={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
                        title={theme === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'}
                    >
                        {theme === 'dark' ? '🌞' : '🌙'}
                    </button>
                </div>
            </>
        )}
    </NavGrid>;
}

type UptimeConfig = {
    startedAt?: number;
};

type FfmpegMutexState = {
    locked: boolean;
    lockedAt?: number;
    label?: string;
};

function formatDuration(milliseconds: number): string {
    if (!Number.isFinite(milliseconds) || milliseconds <= 0) return "0s";
    const totalSeconds = Math.floor(milliseconds / 1000);
    const seconds = totalSeconds % 60;
    const totalMinutes = Math.floor(totalSeconds / 60);
    const minutes = totalMinutes % 60;
    const totalHours = Math.floor(totalMinutes / 60);
    const hours = totalHours % 24;
    const days = Math.floor(totalHours / 24);

    if (days > 0) return hours > 0 ? `${days}d ${hours}h` : `${days}d`;
    if (hours > 0) return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`;
    if (minutes > 0) return seconds > 0 ? `${minutes}m ${seconds}s` : `${minutes}m`;
    return `${seconds}s`;
}

const UptimeBadge: React.FC = () => {
    const {data} = useAbortableSWR<UptimeConfig>("/api/config", fetcher);
    const [now, setNow] = React.useState(() => Date.now());

    React.useEffect(() => {
        if (!data?.startedAt) return;
        const intervalId = window.setInterval(() => {
            setNow(Date.now());
        }, 1000);
        return () => window.clearInterval(intervalId);
    }, [data?.startedAt]);

    const duration = data?.startedAt
        ? formatDuration(Math.max(0, now - data.startedAt))
        : "--";

    return (
        <div
            className="pointer-events-none absolute bottom-2 right-2 text-[10px] font-mono text-gray-500 dark:text-gray-400">
            up: {duration}
        </div>
    );
};

const FfmpegMutexIndicator: React.FC = () => {
    const {data} = useAbortableSWR<FfmpegMutexState>("/api/ffmpeg/mutex", fetcher, {
        refreshInterval: 500,
    });
    const locked = data?.locked;
    const isUnknown = locked === undefined;
    const statusLabel = isUnknown ? "FFmpeg status unknown" : locked ? "FFmpeg busy" : "FFmpeg idle";
    const detailLabel = locked ? (data?.label ?? "FFmpeg busy") : null;
    const dotClass = isUnknown
        ? "bg-gray-400 dark:bg-gray-500"
        : locked
            ? "bg-red-500"
            : "bg-green-500";

    return (
        <span className="inline-flex items-center gap-1">
            <span
                className={`inline-flex h-2.5 w-2.5 rounded-full ${dotClass}`}
                role="status"
                aria-label={statusLabel}
                title={statusLabel}
            />
            {detailLabel && (
                <span className="text-[10px] font-mono text-gray-500 dark:text-gray-400">
                    {detailLabel}
                </span>
            )}
        </span>
    );
};

const App: React.FC = () => {
    return (
        <HashRouter>
            <ThemeProvider>
                <PlaylistProvider>
                    <div
                        className={"relative min-h-screen bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 transition-colors duration-200"}>
                        <div className="absolute top-2 left-2 z-50 leading-none">
                            <FfmpegMutexIndicator/>
                        </div>
                        <VStack className={"min-h-screen p-8 bg-inherit"}>
                        <NavigationBar/>

                        {/* A <Switch> looks through its children <Route>s and
                renders the first one that matches the current URL. */}
                        <Switch>
                            <Route path="/downloads">
                                <>
                                    <Add/>
                                    <Downloads/>
                                </>
                            </Route>
                            <Route path="/mediaLibrary/:query">
                                <MediaLibrary/>
                            </Route>
                            <Route path="/mediaLibrary">
                                <MediaLibrary/>
                            </Route>
                            <Route path="/movie/:id/convert">
                                <Convert/>
                            </Route>
                            <Route path="/movie/:id">
                                <Movie/>
                            </Route>
                            <Route path="/search">
                                <Search/>
                            </Route>
                            <Route path="/duplicates">
                                <DuplicatesManager/>
                            </Route>
                            <Route path="/tags">
                                <ConfigurationManager endpoint={"autotags"}/>
                            </Route>
                            <Route path="/queries">
                                <ConfigurationManager endpoint={"queries"}/>
                            </Route>
                            <Route path="/log">
                                <Log/>
                            </Route>
                            <Route path="/subtitleKitchen">
                                <SubtitleKitchen/>
                            </Route>
                            <Route path="/upload">
                                <Upload/>
                            </Route>
                            <Route path="/debug/thumbnails">
                                <ThumbnailDebug/>
                            </Route>
                            <Route path="/admin">
                                <Admin/>
                            </Route>
                        </Switch>
                    </VStack>
                        <UptimeBadge/>
                </div>
                </PlaylistProvider>
            </ThemeProvider>
        </HashRouter>
    );
};

export default App;
