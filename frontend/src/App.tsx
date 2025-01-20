import * as React from "react";

import {HashRouter, Link, Route, Switch} from "react-router-dom";
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
import {NavGrid, VStack} from "./components/Layout";
import {ThemeProvider, useTheme} from "./context/ThemeContext";
import {commonStyles} from "./styles/common";

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
                    <Link to="/mediaLibrary" className={commonStyles.navLink}>Media Library</Link>
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

const App: React.FC = () => {
    return (
        <HashRouter>
            <ThemeProvider>
                <div
                    className={"min-h-screen bg-white dark:bg-gray-900 text-gray-900 dark:text-gray-100 transition-colors duration-200"}>
                    <VStack className={"h-screen p-8"}>
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
                        </Switch>
                    </VStack>
                </div>
            </ThemeProvider>
        </HashRouter>
    );
};

export default App;
