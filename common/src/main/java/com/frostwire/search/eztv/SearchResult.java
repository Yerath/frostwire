package com.frostwire.search.eztv;

/**
 * Created by Yerath on 2-3-2017.
 */
public interface SearchResult {
    long getSize();

    long getCreationTime();

    String getSource();

    String getHash();

    int getSeeds();

    String getDetailsUrl();

    String getDisplayName();

    String getFilename();

    String getTorrentUrl();
}
