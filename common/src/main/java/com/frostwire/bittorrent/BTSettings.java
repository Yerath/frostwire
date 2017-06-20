package com.frostwire.bittorrent;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import com.frostwire.jlibtorrent.swig.*;

import java.io.File;
import java.io.FilenameFilter;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

/**
 * Created by yerath on 20/06/2017.
 */
public class BTSettings extends SessionManager {
    private SessionParams sessionParams;

    public BTSettings () {
        sessionParams = loadSettings();
    }

    public static SessionParams retrieveSettings() {
        return sessionParams;
    }

    private SessionParams defaultParams() {
        SettingsPack sp = setDefaultSettings();
        SessionParams params = new SessionParams(sp);
        return params;
    }
    private static SettingsPack setDefaultSettings() {
        SettingsPack sp = new SettingsPack();

        sp.broadcastLSD(true);

        if (ctx.optimizeMemory) {
            int maxQueuedDiskBytes = sp.maxQueuedDiskBytes();
            sp.maxQueuedDiskBytes(maxQueuedDiskBytes / 2);
            int sendBufferWatermark = sp.sendBufferWatermark();
            sp.sendBufferWatermark(sendBufferWatermark / 2);
            sp.cacheSize(256);
            sp.activeDownloads(4);
            sp.activeSeeds(4);
            sp.maxPeerlistSize(200);
            //sp.setGuidedReadCache(true);
            sp.tickInterval(1000);
            sp.inactivityTimeout(60);
            sp.seedingOutgoingConnections(false);
            sp.connectionsLimit(200);
        } else {
            sp.activeDownloads(10);
            sp.activeSeeds(10);
        }

        return sp;
    }

    private SessionParams loadSettings() {
        try {
            //Retrieve file
            File f = settingsFile();
            if (f.exists()) {
                return retrieveSettingsFromFile();
            } else {
                return defaultParams();
            }
        } catch (Throwable e) {
            LOG.error("Error loading session state", e);
            return defaultParams();
        }
    }

    private SessionParams retrieveSettingsFromFile() {
        try{
            bdecode_node n = decodeSettingsFile();
            return convertDecodeSettingsToSessionParams(n);
        }catch (IOException e){
            return defaultParams();
        }
    }


    private bdecode_node decodeSettingsFile() throws IOException { {
        //Decode the file.
        byte[] data = FileUtils.readFileToByteArray(f);
        byte_vector buffer = Vectors.bytes2byte_vector(data);
        bdecode_node n = new bdecode_node();
        error_code ec = new error_code();

        int ret = bdecode_node.bdecode(buffer, n, ec);
        if( ret == 0) {
            return n;
        }

        throw new IOException("Unable to decode file due to: " + ec.message());
    }

    private SessionParams convertDecodeSettingsToSessionParams(n) {

        if( ret == 0) {
            String stateVersion = n.dict_find_string_value_s(STATE_VERSION_KEY);
            if (!STATE_VERSION_VALUE.equals(stateVersion)) {
                return defaultParams();
            }
            session_params params = libtorrent.read_session_params(n);
            buffer.clear(); // prevents GC
            return new SessionParams(params);
        }else{
            LOG.error("Can't decode session state data: " + ec.message());
            return defaultParams();
        }
    }



    public static void saveSettings() {
        if (swig() == null) {
            return;
        }

        try {
            byte[] data = saveState();
            FileUtils.writeByteArrayToFile(settingsFile(), data);
        } catch (Throwable e) {
            LOG.error("Error saving session state", e);
        }
    }

    public static void revertToDefaultConfiguration() {
        if (swig() == null) {
            return;
        }

        SettingsPack sp = defaultSettings();

        applySettings(sp);
    }

    File settingsFile() {
        return new File(ctx.homeDir, "settings.dat");
    }


}
