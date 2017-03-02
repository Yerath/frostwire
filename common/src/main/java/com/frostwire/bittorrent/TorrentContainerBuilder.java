package com.frostwire.bittorrent;

import java.io.File;
import java.util.List;

public class TorrentContainerBuilder {
    private TorrentInfo ti;
    private File saveDir;
    private Priority[] priorities;
    private File resumeFile;
    private List<TcpEndpoint> peers;

    public TorrentContainerBuilder setTi(TorrentInfo ti) {
        this.ti = ti;
        return this;
    }

    public TorrentContainerBuilder setSaveDir(File saveDir) {
        this.saveDir = saveDir;
        return this;
    }

    public TorrentContainerBuilder setPriorities(Priority[] priorities) {
        this.priorities = priorities;
        return this;
    }

    public TorrentContainerBuilder setResumeFile(File resumeFile) {
        this.resumeFile = resumeFile;
        return this;
    }

    public TorrentContainerBuilder setPeers(List<TcpEndpoint> peers) {
        this.peers = peers;
        return this;
    }

    public TorrentContainer createTorrentContainer() {
        return new TorrentContainer(ti, saveDir, priorities, resumeFile, peers);
    }
}