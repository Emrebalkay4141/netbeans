/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.modules.gradle;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import org.gradle.tooling.BuildAction;
import org.gradle.tooling.BuildActionExecuter;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnectionException;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;
import org.gradle.tooling.ResultHandler;
import org.gradle.tooling.TestLauncher;
import org.netbeans.api.project.Project;
import org.netbeans.modules.gradle.api.NbGradleProject;
import org.netbeans.modules.gradle.api.execute.GradleDistributionManager;
import org.netbeans.modules.gradle.api.execute.GradleDistributionManager.GradleDistribution;
import org.netbeans.modules.gradle.spi.execute.GradleDistributionProvider;
import org.netbeans.spi.project.ProjectServiceProvider;
import org.openide.filesystems.FileUtil;
import org.openide.util.WeakListeners;

/**
 *
 * @author lkishalmi
 */
@ProjectServiceProvider(service = ProjectConnection.class, projectType = NbGradleProject.GRADLE_PROJECT_TYPE)
public final class GradleProjectConnection implements ProjectConnection {

    final Project project;
    ProjectConnection conn;
    ProjectConnection compatConn;
    final ChangeListener listener = (ChangeEvent e) -> {
        close();
    };

    public GradleProjectConnection(Project project) {
        this.project = project;
    }
    
    @Override
    public <T> T getModel(Class<T> type) throws GradleConnectionException, IllegalStateException {
        return getConnection(true).getModel(type);
    }

    @Override
    public <T> void getModel(Class<T> type, ResultHandler<? super T> rh) throws IllegalStateException {
        getConnection(true).getModel(type, rh);
    }

    @Override
    public BuildLauncher newBuild() {
        return getConnection(false).newBuild();
    }

    @Override
    public TestLauncher newTestLauncher() {
        return getConnection(false).newTestLauncher();
    }

    @Override
    public <T> ModelBuilder<T> model(Class<T> type) {
        return getConnection(true).model(type);
    }

    @Override
    public <T> BuildActionExecuter<T> action(BuildAction<T> action) {
        return getConnection(true).action(action);
    }

    @Override
    public BuildActionExecuter.Builder action() {
        return getConnection(true).action();
    }

    @Override
    public void notifyDaemonsAboutChangedPaths(List<Path> list) {
        getConnection(false).notifyDaemonsAboutChangedPaths(list);
    }

    @Override
    public synchronized void close() {
        if (conn != null) {
            conn.close();
        }
        if (conn != compatConn) {
            compatConn.close();
        }
        conn = null;
        compatConn = null;
    }

    synchronized boolean hasConnection() {
        return conn != null || compatConn != null;
    }
    
    private synchronized ProjectConnection getConnection(boolean compatible) {
        if (conn == null) {
            File projectDir = FileUtil.toFile(project.getProjectDirectory());
            GradleConnector gconn = GradleConnector.newConnector();
            GradleDistributionProvider pvd = project.getLookup().lookup(GradleDistributionProvider.class);
            if (pvd != null) {
                pvd.addChangeListener(WeakListeners.change(listener, pvd));
                GradleDistribution dist = pvd.getGradleDistribution();
                if (dist != null) {
                    conn = createConnection(dist, projectDir);
                    if (dist.isCompatibleWithSystemJava()) {
                        compatConn = conn;
                    } else {
                        GradleDistribution compatDist = GradleDistributionManager.get(dist.getGradleUserHome()).defaultDistribution();
                        compatConn = createConnection(compatDist, projectDir);
                    }
                }
            }
            if (conn == null) {
                conn = gconn.forProjectDirectory(projectDir).connect();
                compatConn = conn;
            }
        }
        return compatible ? compatConn : conn;
    }

    private static ProjectConnection createConnection(GradleDistribution dist, File projectDir) {
        GradleConnector gconn = GradleConnector.newConnector();
        gconn = gconn.useGradleUserHomeDir(dist.getGradleUserHome());
        if (dist.isAvailable()) {
            gconn = gconn.useInstallation(dist.getDistributionDir());
        } else {
            gconn = gconn.useDistribution(dist.getDistributionURI());
        }
        return gconn.forProjectDirectory(projectDir).connect();
    }
}
