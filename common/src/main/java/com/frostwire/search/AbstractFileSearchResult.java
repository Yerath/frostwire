/*
 * Created by Angel Leon (@gubatron), Alden Torres (aldenml)
 * Copyright (c) 2011-2014, FrostWire(R). All rights reserved.
 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.frostwire.search;

import com.frostwire.licenses.License;

/**
 * 
 * @author gubatron
 * @author aldenml
 *
 */
public abstract class AbstractFileSearchResult implements FileSearchResult {
    protected final MyAbstractSearchResult abstractSearchResult = new MyAbstractSearchResult();

    protected abstract String getDisplayName();

    protected abstract String getDetailsUrl();

    protected abstract String getSource();

    public License getLicense() {
        return abstractSearchResult.getLicense();
    }

    public long getCreationTime() {
        return abstractSearchResult.getCreationTime();
    }

    public String toString() {
        return abstractSearchResult.toString();
    }

    public String getThumbnailUrl() {
        return abstractSearchResult.getThumbnailUrl();
    }

    public int uid() {
        return abstractSearchResult.uid();
    }

    private class MyAbstractSearchResult extends AbstractSearchResult {
        public String getDisplayName() {
            return AbstractFileSearchResult.this.getDisplayName();
        }

        public String getDetailsUrl() {
            return AbstractFileSearchResult.this.getDetailsUrl();
        }

        public String getSource() {
            return AbstractFileSearchResult.this.getSource();
        }
    }
}