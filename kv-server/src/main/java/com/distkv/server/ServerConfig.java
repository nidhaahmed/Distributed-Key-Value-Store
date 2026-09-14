package com.distkv.server;

import com.distkv.persistence.FsyncPolicy;

/**
 * Server configuration parameters.
 */
public class ServerConfig {

    private String host = "0.0.0.0";
    private int port = 7379;
    private int threadPoolSize = 64;
    private boolean persistenceEnabled = true;
    private String aofFilePath = "data/store.aof";
    private FsyncPolicy fsyncPolicy = FsyncPolicy.EVERYSEC;
    private boolean lruEnabled = false;
    private int lruCapacity = 100_000;
    private boolean clusterEnabled = false;
    private String nodeId = "node-1";
    private String clusterNodes = null;
    private int virtualNodes = 150;
    private String routingMode = "PROXY";

    public ServerConfig() {}

    public String getHost() {
        return host;
    }

    public ServerConfig setHost(String host) {
        this.host = host;
        return this;
    }

    public int getPort() {
        return port;
    }

    public ServerConfig setPort(int port) {
        this.port = port;
        return this;
    }

    public int getThreadPoolSize() {
        return threadPoolSize;
    }

    public ServerConfig setThreadPoolSize(int threadPoolSize) {
        this.threadPoolSize = threadPoolSize;
        return this;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }

    public ServerConfig setPersistenceEnabled(boolean persistenceEnabled) {
        this.persistenceEnabled = persistenceEnabled;
        return this;
    }

    public String getAofFilePath() {
        return aofFilePath;
    }

    public ServerConfig setAofFilePath(String aofFilePath) {
        this.aofFilePath = aofFilePath;
        return this;
    }

    public FsyncPolicy getFsyncPolicy() {
        return fsyncPolicy;
    }

    public ServerConfig setFsyncPolicy(FsyncPolicy fsyncPolicy) {
        this.fsyncPolicy = fsyncPolicy;
        return this;
    }

    public boolean isLruEnabled() {
        return lruEnabled;
    }

    public ServerConfig setLruEnabled(boolean lruEnabled) {
        this.lruEnabled = lruEnabled;
        return this;
    }

    public int getLruCapacity() {
        return lruCapacity;
    }

    public ServerConfig setLruCapacity(int lruCapacity) {
        this.lruCapacity = lruCapacity;
        return this;
    }

    public boolean isClusterEnabled() {
        return clusterEnabled;
    }

    public ServerConfig setClusterEnabled(boolean clusterEnabled) {
        this.clusterEnabled = clusterEnabled;
        return this;
    }

    public String getNodeId() {
        return nodeId;
    }

    public ServerConfig setNodeId(String nodeId) {
        this.nodeId = nodeId;
        return this;
    }

    public String getClusterNodes() {
        return clusterNodes;
    }

    public ServerConfig setClusterNodes(String clusterNodes) {
        this.clusterNodes = clusterNodes;
        return this;
    }

    public int getVirtualNodes() {
        return virtualNodes;
    }

    public ServerConfig setVirtualNodes(int virtualNodes) {
        this.virtualNodes = virtualNodes;
        return this;
    }

    public String getRoutingMode() {
        return routingMode;
    }

    public ServerConfig setRoutingMode(String routingMode) {
        this.routingMode = routingMode;
        return this;
    }
}
