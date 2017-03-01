package com.frostwire.bittorrent;

import java.io.File;
import java.util.List;

public class TorrentContainer {
    private final TorrentInfo ti;
    private final File saveDir;
    private final Priority[] priorities;
    private final File resumeFile;
    private final List<TcpEndpoint> peers;

    public TorrentContainer(TorrentInfo ti, File saveDir, Priority[] priorities, File resumeFile, List<TcpEndpoint> peers) {
        this.ti = ti;
        this.saveDir = saveDir;
        this.priorities = priorities;
        this.resumeFile = resumeFile;
        this.peers = peers;
    }

    public TorrentInfo getTi() {
        return ti;
    }

    public File getSaveDir() {
        return saveDir;
    }

    public Priority[] getPriorities() {
        return priorities;
    }

    public File getResumeFile() {
        return resumeFile;
    }

    public List<TcpEndpoint> getPeers() {
        return peers;
    }
}
