import * as React from "react";
import {MainHeading, SectionHeading} from "../components/Typography";

export function SubtitleKitchen() {
    return <>
        <MainHeading>
            Subtitle Kitchen!
        </MainHeading>
        <SectionHeading>
            Shift subtitles
        </SectionHeading>

        <form action="/api/shiftsubtitles" method="POST" encType="multipart/form-data">
            <div>
                <label>
                    Subtitle file:
                    <input type="file" multiple={true} name="subtitleFile"/>
                </label>
                <label>
                    Offset (milliseconds):
                    <input type="number" name="offset"/>
                </label>
            </div>
            <button type="submit">Submit</button>
        </form>
        <SectionHeading>
            Embed subtitles (WIP)
        </SectionHeading>
        <form action="/api/embedsubtitles" method="POST" encType="multipart/form-data">
            <div>
                <label>
                    Video file:
                    <input type="file" name="file1"/>
                </label>
            </div>
            <div>
                <label>
                    Subtitle file:
                    <input type="file" name="file2"/>
                </label>
            </div>
            <div>
                <label>
                    Offset (milliseconds):
                    <input type="number" name="offset"/>
                </label>
            </div>
            <button type="submit">Submit</button>
        </form>
    </>;
}