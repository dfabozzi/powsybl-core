package com.powsybl.afs.ws.storage.st;


import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.powsybl.afs.ProjectFile;
import com.powsybl.afs.TaskListener;
import com.powsybl.afs.TaskMonitor;
import com.powsybl.afs.ws.client.utils.UncheckedDeploymentException;
import com.powsybl.afs.ws.storage.TaskEventClient;
import com.powsybl.afs.ws.utils.AfsRestApi;

import static com.powsybl.afs.ws.storage.st.RemoteAppStorageSt.getWebTarget;
import static com.powsybl.afs.ws.storage.st.RemoteAppStorageSt.createClient;
import static com.powsybl.afs.ws.storage.st.RemoteListenableAppStorageSt.getWebSocketUri;

public class RemoteTaskMonitorSt implements TaskMonitor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RemoteTaskMonitorSt.class);
    public static final String FILE_SYSTEM_NAME = "fileSystemName";
    private final String fileSystemName;
    private final URI restUri;
    private final String token;
    private final Map<TaskListener, Session> sessions = new HashMap<>();
    private final RestTemplate client;
    private final UriComponentsBuilder webTarget;

    public RemoteTaskMonitorSt(String fileSystemName, URI restUri, String token) {
        this.fileSystemName = Objects.requireNonNull(fileSystemName);
        this.restUri = Objects.requireNonNull(restUri);
        this.token = token;
        client = createClient();
        webTarget = getWebTarget(restUri);
    }
    @Override
    public Task startTask(ProjectFile projectFile) {
        Objects.requireNonNull(projectFile);

        LOGGER.debug("startTask(fileSystemName={}, projectFile={})", fileSystemName, projectFile.getId());
/*
        Response response = webTarget.path("fileSystems/{fileSystemName}/tasks")
                .resolveTemplate(FILE_SYSTEM_NAME, fileSystemName)
                .queryParam("projectFileId", projectFile.getId())
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token)
                .put(Entity.text(""));
        try {
            return readEntityIfOk(response, TaskMonitor.Task.class);
        } finally {
            response.close();
        }
*/
        UriComponentsBuilder webTargetTemp = webTarget.cloneBuilder();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.add(HttpHeaders.AUTHORIZATION, token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        Map<String, String> params = new HashMap<String, String>();
        params.put(FILE_SYSTEM_NAME, fileSystemName);

        URI uri = webTargetTemp
                .path("fileSystems/{fileSystemName}/tasks")
                .queryParam("projectFileId", projectFile.getId())
                .buildAndExpand(params)
                .toUri();
        ResponseEntity<Task> response = client.exchange(
                    uri,
                    HttpMethod.PUT,
                    entity,
                    Task.class
                    );
        Task task = response.getBody();
        return task;
    }
    @Override
    public void stopTask(UUID id) {
        LOGGER.debug("stopTask(fileSystemName={}, id={})", fileSystemName, id);

        /*Response response = webTarget.path("fileSystems/{fileSystemName}/tasks/{taskId}")
                .resolveTemplate(FILE_SYSTEM_NAME, fileSystemName)
                .resolveTemplate("taskId", id)
                .request()
                .header(HttpHeaders.AUTHORIZATION, token)
                .delete();
        try {
            checkOk(response);
        } finally {
            response.close();
        }*/
        UriComponentsBuilder webTargetTemp = webTarget.cloneBuilder();

        HttpHeaders headers = new HttpHeaders();
        //headers.setContentType(MediaType.APPLICATION_JSON);
        //headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.add(HttpHeaders.AUTHORIZATION, token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        Map<String, String> params = new HashMap<String, String>();
        params.put(FILE_SYSTEM_NAME, fileSystemName);
        params.put("taskId", id.toString());

        URI uri = webTargetTemp
                .path("fileSystems/{fileSystemName}/tasks/{taskId}")
                .buildAndExpand(params)
                .toUri();

        ResponseEntity<String> response = client.exchange(
                uri,
                HttpMethod.DELETE,
                entity,
                String.class
                );
    }
    @Override
    public void updateTaskMessage(UUID id, String message) {
        LOGGER.debug("updateTaskMessage(fileSystemName={}, id={})", fileSystemName, id);

        /*Response response = webTarget.path("fileSystems/{fileSystemName}/tasks/{taskId}")
                .resolveTemplate(FILE_SYSTEM_NAME, fileSystemName)
                .resolveTemplate("taskId", id)
                .request()
                .header(HttpHeaders.AUTHORIZATION, token)
                .post(Entity.text(message));
        try {
            checkOk(response);
        } finally {
            response.close();
        }*/
        UriComponentsBuilder webTargetTemp = webTarget.cloneBuilder();

        HttpHeaders headers = new HttpHeaders();
        //headers.setContentType(MediaType.APPLICATION_JSON);
        //headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.add(HttpHeaders.AUTHORIZATION, token);

        HttpEntity<String> entity = new HttpEntity<>(message, headers);

        Map<String, String> params = new HashMap<String, String>();
        params.put(FILE_SYSTEM_NAME, fileSystemName);
        params.put("taskId", id.toString());

        URI uri = webTargetTemp
                .path("fileSystems/{fileSystemName}/tasks/{taskId}")
                .buildAndExpand(params)
                .toUri();

        ResponseEntity<String> response = client.exchange(
                uri,
                HttpMethod.POST,
                entity,
                String.class
                );
    }
    @Override
    public Snapshot takeSnapshot(String projectId) {
        LOGGER.debug("takeSnapshot(fileSystemName={}, projectId={})", fileSystemName, projectId);

        /*Response response = webTarget.path("fileSystems/{fileSystemName}/tasks")
                .resolveTemplate(FILE_SYSTEM_NAME, fileSystemName)
                .queryParam("projectId", projectId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token)
                .get();
        try {
            return readEntityIfOk(response, Snapshot.class);
        } finally {
            response.close();
        }*/
        UriComponentsBuilder webTargetTemp = webTarget.cloneBuilder();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.add(HttpHeaders.AUTHORIZATION, token);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        Map<String, String> params = new HashMap<String, String>();
        params.put(FILE_SYSTEM_NAME, fileSystemName);

        URI uri = webTargetTemp
                .path("fileSystems/{fileSystemName}/tasks/{taskId}")
                .queryParam("projectId", projectId)
                .buildAndExpand(params)
                .toUri();

        ResponseEntity<Snapshot> response = client.exchange(
                uri,
                HttpMethod.POST,
                entity,
                Snapshot.class
                );
        return response.getBody();
    }

    @Override
    public void addListener(TaskListener listener) {
        Objects.requireNonNull(listener);

        URI wsUri = getWebSocketUri(restUri);
        URI endPointUri = URI.create(wsUri + "/messages/" + AfsRestApi.RESOURCE_ROOT + "/" +
                AfsRestApi.VERSION + "/task_events/" + fileSystemName + "/" + listener.getProjectId());

        LOGGER.debug("Connecting to task event websocket for file system {} at {}", fileSystemName, endPointUri);

        WebSocketContainer container = ContainerProvider.getWebSocketContainer();
        try {
            Session session = container.connectToServer(new TaskEventClient(listener), endPointUri);
            sessions.put(listener, session);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (DeploymentException e) {
            throw new UncheckedDeploymentException(e);
        }
    }

    @Override
    public void removeListener(TaskListener listener) {
        Objects.requireNonNull(listener);

        Session session = sessions.remove(listener);
        if (session != null) {
            try {
                session.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void close() {
        for (Session session : sessions.values()) {
            try {
                session.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        //client.close();
    }
}