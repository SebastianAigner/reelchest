# Similarity search for videos

## Current implementation

- Each picture has a vector representation (binary vector, phash)
- We take 100 random pictures from a video
- We find the global minimum: which of the other library entries has the lowest cumulative distance for those 100
  pictures?

## A new way

Some observations:

- Length of videos can contribute to duplicate candidates
    - (though, if one video is an excerpt of another video, it won't be detected -- e.g. a highlight clip won't show any
      relation to the full video)
- Probably, the waveform of the audio would be enough to get a good heuristic?
    - (though, if it's a dub / foreign version of the same piece of media, then they won't be labelled)

