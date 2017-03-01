package com.frostwire.bittorrent;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class ExtraData {
    public Map<String, String> invoke() {
        Map<String, String> map = new HashMap<>();
        String infoHash = getInfoHash();
        File file = engine.resumeDataFile(infoHash);

        if (file.exists()) {
            string_entry_map dictionary = readExtraData(file);
            map = putStringEntryMapIntoMap(dictionary);
        }

        return map;
    }

    /* @CLEANCODE: Dimitry Volker */
    private string_entry_map readExtraData(File file){
        try{
            byte[] arr = FileUtils.readFileToByteArray(file);
            entry e = entry.bdecode(Vectors.bytes2byte_vector(arr));
            string_entry_map d = e.dict();
            if (d.has_key(EXTRA_DATA_KEY)) {
                return d.get(EXTRA_DATA_KEY).dict();
            }
        }catch (Exception e){
            LOG.error("Error reading extra data from resume file", e);
        }
        return new string_entry_map();
    }

    public Map<String, String> putStringEntryMapIntoMap(string_entry_map dictionary){
        Map<String, String> map = new HashMap<>();
        string_vector keys = dictionary.keys();
        int size = (int) keys.size();

        for (int i = 0; i < size; i++) {
            String key = keys.get(i);
            entry e = dictionary.get(k);
            if (e.type() == entry.data_type.string_t) {
                map.put(key, e.string());
            }
        }
        return map;
    }
}