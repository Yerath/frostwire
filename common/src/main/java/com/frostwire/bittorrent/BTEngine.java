/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2017, FrostWire(R). All rights reserved.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.frostwire.bittorrent;

import com.frostwire.jlibtorrent.AlertListener;
import com.frostwire.jlibtorrent.Entry;
import com.frostwire.jlibtorrent.Priority;
import com.frostwire.jlibtorrent.SessionManager;
import com.frostwire.jlibtorrent.SessionParams;
import com.frostwire.jlibtorrent.SettingsPack;
import com.frostwire.jlibtorrent.TcpEndpoint;
import com.frostwire.jlibtorrent.TorrentHandle;
import com.frostwire.jlibtorrent.TorrentInfo;
import com.frostwire.jlibtorrent.Vectors;
import com.frostwire.jlibtorrent.alerts.Alert;
import com.frostwire.jlibtorrent.alerts.AlertType;
import com.frostwire.jlibtorrent.alerts.ExternalIpAlert;
import com.frostwire.jlibtorrent.alerts.FastresumeRejectedAlert;
import com.frostwire.jlibtorrent.alerts.ListenFailedAlert;
import com.frostwire.jlibtorrent.alerts.ListenSucceededAlert;
import com.frostwire.jlibtorrent.alerts.TorrentAlert;
import com.frostwire.jlibtorrent.swig.bdecode_node;
import com.frostwire.jlibtorrent.swig.byte_vector;
import com.frostwire.jlibtorrent.swig.entry;
import com.frostwire.jlibtorrent.swig.error_code;
import com.frostwire.jlibtorrent.swig.libtorrent;
import com.frostwire.jlibtorrent.swig.session_params;
import com.frostwire.jlibtorrent.swig.settings_pack;
import com.frostwire.platform.FileSystem;
import com.frostwire.platform.Platforms;
import com.frostwire.search.torrent.TorrentCrawledSearchResult;
import com.frostwire.util.Logger;

import javafx.scene.layout.Priority;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import static com.frostwire.jlibtorrent.alerts.AlertType.ADD_TORRENT;
import static com.frostwire.jlibtorrent.alerts.AlertType.EXTERNAL_IP;
import static com.frostwire.jlibtorrent.alerts.AlertType.FASTRESUME_REJECTED;
import static com.frostwire.jlibtorrent.alerts.AlertType.LISTEN_FAILED;
import static com.frostwire.jlibtorrent.alerts.AlertType.LISTEN_SUCCEEDED;
import static com.frostwire.jlibtorrent.alerts.AlertType.PEER_LOG;
import static com.frostwire.jlibtorrent.alerts.AlertType.TORRENT_LOG;

/**
 * @author gubatron
 * @author aldenml
 */
public final class BTEngine extends SessionManager {

    private static final Logger LOG = Logger.getLogger(BTEngine.class);

    private static final int[] INNER_LISTENER_TYPES = new int[]{
            ADD_TORRENT.swig(),
            LISTEN_SUCCEEDED.swig(),
            LISTEN_FAILED.swig(),
            EXTERNAL_IP.swig(),
            FASTRESUME_REJECTED.swig(),
            TORRENT_LOG.swig(),
            PEER_LOG.swig(),
            AlertType.LOG.swig()
    };

    private static final String TORRENT_ORIG_PATH_KEY = "torrent_orig_path";
    private static final String STATE_VERSION_KEY = "state_version";


    // this constant only changes when the libtorrent settings_pack ABI is
    // incompatible with the previous version, it should only happen from
    // time to time, not in every version
    private static final String STATE_VERSION_VALUE = "1.2.0.6";
    /*
     * Data class for getting defaul directories
     */
    public static final BTContext ctx = new BTContext();
    private static final BTFile btFile = new BTFile(ctx);

    private final InnerListener innerListener;
    private final Queue<RestoreDownloadTask> restoreDownloadsQueue;

    private BTEngineListener listener;

    private BTEngine() {
        super(false);
        this.innerListener = new InnerListener();
        this.restoreDownloadsQueue = new LinkedList<>();
    }

    private static class Loader {
        static final BTEngine INSTANCE = new BTEngine();
        private Loader() {
        }
    }

    public static BTEngine getInstance() {
        if (ctx == null) {
            throw new IllegalStateException("Context can't be null");
        }
        return Loader.INSTANCE;
    }

    public BTEngineListener getListener() {
        return listener;
    }

    public void setListener(BTEngineListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        BTSettings btSettings = new BTSettings();
        SessionParams params = BTSettings.retrieveSettings();

        settings_pack sp = params.settings().swig();
        sp.set_str(settings_pack.string_types.listen_interfaces.swigValue(), ctx.interfaces);
        sp.set_int(settings_pack.int_types.max_retry_port_bind.swigValue(), ctx.retries);
        sp.set_str(settings_pack.string_types.dht_bootstrap_nodes.swigValue(), dhtBootstrapNodes());
        sp.set_int(settings_pack.int_types.active_limit.swigValue(), 2000);
        sp.set_int(settings_pack.int_types.stop_tracker_timeout.swigValue(), 0);

        super.start(params);
    }

    @Override
    protected void onBeforeStart() {
        addListener(innerListener);
    }

    @Override
    protected void onAfterStart() {
        fireStarted();
    }

    @Override
    protected void onBeforeStop() {
        removeListener(innerListener);
        BTSettings.saveSettings();
    }

    @Override
    protected void onApplySettings(SettingsPack sp) {
        saveSettings();
    }

    @Override
    protected void onAfterStop() {
        fireStopped();
    }

    @Override
    public void moveStorage(File dataDir) {
        if (swig() == null) {
            return;
        }

        ctx.dataDir = dataDir; // this will be removed when we start using platform

        super.moveStorage(dataDir);
    }

    public void download(File torrent, File saveDir, boolean[] selection) {
        if (swig() == null) {
            return;
        }

        saveDir = btFile.setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }

        TorrentInfo ti = new TorrentInfo(torrent);

        Priority[] priorities = null;

        TorrentHandle th = find(ti.infoHash());
        boolean exists = th != null;

        if (selection != null) {
            if (th != null) {
                priorities = th.filePriorities();
            } else {
                priorities = Priority.array(Priority.IGNORE, ti.numFiles());
            }

            for (int i = 0; i < selection.length; i++) {
                if (selection[i]) {
                    priorities[i] = Priority.NORMAL;
                }
            }
        }

        download(new TorrentContainerBuilder().setTi(ti).setSaveDir(saveDir).setPriorities(priorities).setResumeFile(null).setPeers(null).createTorrentContainer());

        if (!exists) {
            btFile.saveResumeTorrent(ti);
        }
    }

    public void download(TorrentInfo ti, File saveDir, boolean[] selection, List<TcpEndpoint> peers) {
        download(ti, saveDir, selection, peers, false);
    }

    public void download(TorrentInfo ti, File saveDir, boolean[] selection, List<TcpEndpoint> peers, boolean dontSaveTorrentFile) {
        if (swig() == null) {
            return;
        }

        saveDir = btFile.setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }

        Priority[] priorities = null;

        TorrentHandle th = find(ti.infoHash());
        boolean torrentHandleExists = th != null;

        if (selection != null) {
            if (torrentHandleExists) {
                priorities = th.filePriorities();
            } else {
                priorities = Priority.array(Priority.IGNORE, ti.numFiles());
            }

            if (priorities != null) {
                for (int i = 0; i < selection.length; i++) {
                    if (selection[i] && i < priorities.length) {
                        priorities[i] = Priority.NORMAL;
                    }
                }
            }
        }

        download(new TorrentContainerBuilder().setTi(ti).setSaveDir(saveDir).setPriorities(priorities).setResumeFile(null).setPeers(peers).createTorrentContainer());

        if (!torrentHandleExists) {
            btFile.saveResumeTorrent(ti);
            if (!dontSaveTorrentFile) {
                btFile.saveTorrent(ti);
            }
        }
    }

    public void download(TorrentCrawledSearchResult sr, File saveDir) {
        download(sr, saveDir, false);
    }

    public void download(TorrentCrawledSearchResult sr, File saveDir, boolean dontSaveTorrentFile) {
        if (swig() == null) {
            return;
        }

        saveDir = btFile.setupSaveDir(saveDir);
        if (saveDir == null) {
            return;
        }

        TorrentInfo ti = sr.getTorrentInfo();
        int fileIndex = sr.getFileIndex();

        TorrentHandle th = find(ti.infoHash());
        boolean exists = th != null;

        if (th != null) {
            Priority[] priorities = th.filePriorities();
            if (priorities[fileIndex] == Priority.IGNORE) {
                priorities[fileIndex] = Priority.NORMAL;
                download(new TorrentContainerBuilder().setTi(ti).setSaveDir(saveDir).setPriorities(priorities).setResumeFile(null).setPeers(null).createTorrentContainer());
            }
        } else {
            Priority[] priorities = Priority.array(Priority.IGNORE, ti.numFiles());
            priorities[fileIndex] = Priority.NORMAL;
            download(new TorrentContainerBuilder().setTi(ti).setSaveDir(saveDir).setPriorities(priorities).setResumeFile(null).setPeers(null).createTorrentContainer());
        }

        if (!exists) {
            btFile.saveResumeTorrent(ti);
            if (!dontSaveTorrentFile) {
                btFile.saveTorrent(ti);
            }
        }
    }

    public void restoreDownloads() {
        if (swig() == null) {
            return;
        }

        if (checkContextDir()) return;

        File[] torrents = ctx.homeDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name != null && FilenameUtils.getExtension(name).equalsIgnoreCase("torrent");
            }
        });

        if (torrents != null) {
            for (File t : torrents) {
                try {
                    String infoHash = FilenameUtils.getBaseName(t.getName());
                    if (infoHash != null) {
                        File resumeFile = btFile.resumeDataFile(infoHash);
                        File savePath = btFile.readSavePath(infoHash);
                        if (btFile.setupSaveDir(savePath) == null) {
                            LOG.warn("Can't create data dir or mount point is not accessible");
                            return;
                        }

                        restoreDownloadsQueue.add(new RestoreDownloadTask(t, null, null, resumeFile));
                    }
                } catch (Throwable e) {
                    LOG.error("Error restoring torrent download: " + t, e);
                }
            }
        }

        migrateVuzeDownloads();

        runNextRestoreDownloadTask();
    }

    private boolean checkContextDir() {
        if (ctx.homeDir == null || !ctx.homeDir.exists()) {
            LOG.warn("Wrong setup with BTEngine home dir");
            return true;
        }
        return false;
    }

    private void fireStarted() {
        if (listener != null) {
            listener.started(this);
        }
    }

    private void fireStopped() {
        if (listener != null) {
            listener.stopped(this);
        }
    }

    private void fireDownloadAdded(TorrentAlert<?> alert) {
        try {
            TorrentHandle th = find(alert.handle().infoHash());
            if (th != null) {
                BTDownload dl = new BTDownload(this, th);
                if (listener != null) {
                    listener.downloadAdded(this, dl);
                }
            } else {
                LOG.info("torrent was not successfully added");
            }
        } catch (Throwable e) {
            LOG.error("Unable to create and/or notify the new download", e);
        }
    }

    private void fireDownloadUpdate(TorrentHandle th) {
        try {
            BTDownload dl = new BTDownload(this, th);
            if (listener != null) {
                listener.downloadUpdate(this, dl);
            }
        } catch (Throwable e) {
            LOG.error("Unable to notify update the a download", e);
        }
    }

    private void migrateVuzeDownloads() {
        try {
            resumeVuzeDownloadsFromFile();
        } catch (Throwable e) {
            LOG.error("Error migrating old vuze downloads", e);
        }
    }

    private void resumeVuzeDownloadsFromFile() {
        File dir = new File(ctx.homeDir.getParent(), "azureus");
        File file = new File(dir, "downloads.config");

        if (file.exists()) {
            restoreOldVuzeDownloadsForAllEntries(file);
        }
    }

    private void restoreOldVuzeDownloadsForAllEntries(File file) {
        Entry configEntry = Entry.bdecode(file);
        List<Entry> downloads = configEntry.dictionary().get("downloads").list();

        for (Entry d : downloads) {
            try {
                restoreOldVuzeDownload(d);
            } catch (Throwable e) {
                LOG.error("Error restoring vuze torrent download", e);
            }
        }

        file.delete();
    }

    private void restoreOldVuzeDownload(Entry d) {
        Map<String, Entry> map = d.dictionary();
        File saveDir = new File(map.get("save_dir").string());
        File torrent = new File(map.get("torrent").string());
        List<Entry> filePriorities = map.get("file_priorities").list();

        Priority[] priorities = setVuzeDownloadPriorities(filePriorities);

        if (torrent.exists() && saveDir.exists()) {
            LOG.info("Restored old vuze download: " + torrent);
            restoreDownloadsQueue.add(new RestoreDownloadTask(torrent, saveDir, priorities, null));
            btFile.saveResumeTorrent(new TorrentInfo(torrent));
        }
    }

    private Priority[] setVuzeDownloadPriorities(List<Entry> filePriorities) {
        Priority[] priorities = Priority.array(Priority.IGNORE, filePriorities.size());
        for (int i = 0; i < filePriorities.size(); i++) {
            long p = filePriorities.get(i).integer();
            if (p != 0) {
                priorities[i] = Priority.NORMAL;
            }
        }
        return priorities;
    }

    private void runNextRestoreDownloadTask() {
        RestoreDownloadTask task = null;
        try {
            if (!restoreDownloadsQueue.isEmpty()) {
                task = restoreDownloadsQueue.poll();
            }
        } catch (Throwable t) {
            // on Android, LinkedList's .poll() implementation throws a NoSuchElementException
        }
        if (task != null) {
            task.run();
        }
    }

    private void download(TorrentContainer torrentContainer) {

        TorrentInfo ti = torrentContainer.getTi();
        TorrentHandle th = find(ti.infoHash());
        Priority[] priorities = torrentContainer.getPriorities();

        if (th != null) {
            // found a download with the same hash, just adjust the priorities if needed
            if (priorities != null) {
                if (ti.numFiles() != priorities.length) {
                    throw new IllegalArgumentException("The priorities length should be equals to the number of files");
                }

                th.prioritizeFiles(priorities);
                fireDownloadUpdate(th);
                th.resume();
            } else {
                // did they just add the entire torrent (therefore not selecting any priorities)
                th.prioritizeFiles(Priority.array(Priority.NORMAL, ti.numFiles()));
                fireDownloadUpdate(th);
                th.resume();
            }
        } else { // new download
            download(ti, torrentContainer.getSaveDir(), torrentContainer.getResumeFile(), priorities, torrentContainer.getPeers());
        }
    }

    private final class InnerListener implements AlertListener {
        @Override
        public int[] types() {
            return INNER_LISTENER_TYPES;
        }

        @Override
        public void alert(Alert<?> alert) {

            AlertType type = alert.type();

            switch (type) {
                case ADD_TORRENT:
                    TorrentAlert<?> torrentAlert = (TorrentAlert<?>) alert;
                    fireDownloadAdded(torrentAlert);
                    runNextRestoreDownloadTask();
                    break;
                case LISTEN_SUCCEEDED:
                    onListenSucceeded((ListenSucceededAlert) alert);
                    break;
                case LISTEN_FAILED:
                    onListenFailed((ListenFailedAlert) alert);
                    break;
                case EXTERNAL_IP:
                    onExternalIpAlert((ExternalIpAlert) alert);
                    break;
                case FASTRESUME_REJECTED:
                    onFastresumeRejected((FastresumeRejectedAlert) alert);
                    break;
                case TORRENT_LOG:
                case PEER_LOG:
                case LOG:
                    printAlert(alert);
                    break;
            }
        }

        private void printAlert(Alert alert) {
            System.out.println("Log: " + alert);
        }

        private void onListenSucceeded(ListenSucceededAlert alert) {
            try {
                String endp = alert.address() + ":" + alert.port();
                String s = "endpoint: " + endp + " type:" + alert.socketType();
                LOG.info("Listen succeeded on " + s);
            } catch (Throwable e) {
                LOG.error("Error adding listen endpoint to internal list", e);
            }
        }

        private void onListenFailed(ListenFailedAlert alert) {
            String endp = alert.address() + ":" + alert.port();
            String s = "endpoint: " + endp + " type:" + alert.socketType();
            String message = alert.error().message();
            LOG.info("Listen failed on " + s + " (error: " + message + ")");
        }
    }

    private void onExternalIpAlert(ExternalIpAlert alert) {
        try {
            // libtorrent perform all kind of tests
            // to avoid non usable addresses
            String address = alert.externalAddress().toString();
            LOG.info("External IP: " + address);
        } catch (Throwable e) {
            LOG.error("Error saving reported external ip", e);
        }
    }

    private void onFastresumeRejected(FastresumeRejectedAlert alert) {
        try {
            LOG.warn("Failed to load fastresume data, path: " + alert.filePath() +
                    ", operation: " + alert.operation() + ", error: " + alert.error().message());
        } catch (Throwable e) {
            LOG.error("Error logging fastresume rejected alert", e);
        }
    }

    private final class RestoreDownloadTask implements Runnable {

        private final File torrent;
        private final File saveDir;
        private final Priority[] priorities;
        private final File resume;

        public RestoreDownloadTask(File torrent, File saveDir, Priority[] priorities, File resume) {
            this.torrent = torrent;
            this.saveDir = saveDir;
            this.priorities = priorities;
            this.resume = resume;
        }

        @Override
        public void run() {
            try {
                download(new TorrentInfo(torrent), saveDir, resume, priorities, null);
            } catch (Throwable e) {
                LOG.error("Unable to restore download from previous session. (" + torrent.getAbsolutePath() + ")", e);
            }
        }
    }

    private static String dhtBootstrapNodes() {
        StringBuilder sb = new StringBuilder();

        sb.append("dht.libtorrent.org:25401").append(",");
        sb.append("router.bittorrent.com:6881").append(",");
        sb.append("dht.transmissionbt.com:6881").append(",");
        // for DHT IPv6
        sb.append("outer.silotis.us:6881");

        return sb.toString();
    }
}
