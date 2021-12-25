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

export default function App() {
    return (
        <HashRouter>
            <div className={"m-8"}>
                <nav className={"grid grid-cols-1 sm:grid-cols-7 gap-4 text-center"}>
                    <div>
                        <Link to="/search">Search</Link>
                    </div>
                    <div>
                        <Link to="/mediaLibrary">Media Library</Link>
                    </div>
                    <div>
                        <Link to="/add">Add</Link>
                    </div>
                    <div>
                        <Link to="/downloads">Downloads</Link>
                    </div>
                    <div>
                        <Link to="/duplicates">Duplicates</Link>
                    </div>
                    <div>
                        <Link to="/tags">Tags</Link>
                    </div>
                    <div>
                        <Link to="/queries">Queries</Link>
                    </div>
                    <div>
                        <Link to="/log">Log</Link>
                    </div>
                </nav>

                {/* A <Switch> looks through its children <Route>s and
            renders the first one that matches the current URL. */}
                <Switch>
                    <Route path="/add">
                        <Add/>
                    </Route>
                    <Route path="/downloads">
                        <Downloads/>
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
                </Switch>
            </div>
        </HashRouter>
    );
}