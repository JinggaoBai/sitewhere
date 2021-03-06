/*
 * Copyright (c) SiteWhere, LLC. All rights reserved. http://www.sitewhere.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package com.sitewhere.microservice.grpc;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import com.sitewhere.grpc.client.common.tracing.ServerTracingInterceptor;
import com.sitewhere.grpc.client.spi.server.IGrpcServer;
import com.sitewhere.server.lifecycle.TenantEngineLifecycleComponent;
import com.sitewhere.spi.SiteWhereException;
import com.sitewhere.spi.server.lifecycle.ILifecycleProgressMonitor;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.netty.channel.nio.NioEventLoopGroup;

/**
 * Base class for GRPC servers used by microservices.
 * 
 * @author Derek
 */
public class GrpcServer extends TenantEngineLifecycleComponent implements IGrpcServer {

    /** Max threads used for executing GPRC requests */
    private static final int THREAD_POOL_SIZE = 25;

    /** Port for GRPC server */
    private int port;

    /** Wrapped GRPC server */
    private Server server;

    /** Service implementation */
    private BindableService serviceImplementation;

    /** Indicates whether to use tracing interceptor */
    private boolean useTracingInterceptor = false;

    /** Interceptor for JWT authentication */
    private JwtServerInterceptor jwtInterceptor;

    /** Interceptor for open tracing APIs */
    private ServerTracingInterceptor tracingInterceptor;

    /** Executor service used to handle GRPC requests */
    private ExecutorService serverExecutor = Executors.newFixedThreadPool(THREAD_POOL_SIZE,
	    new GrpcServerThreadFactory());

    public GrpcServer(BindableService serviceImplementation, int port) {
	this.serviceImplementation = serviceImplementation;
	this.port = port;
    }

    /**
     * Build server component based on configuration.
     * 
     * @return
     */
    protected Server buildServer() {
	NettyServerBuilder builder = NettyServerBuilder.forPort(port);
	builder.addService(getServiceImplementation()).intercept(getJwtInterceptor());
	builder.executor(getServerExecutor());
	builder.bossEventLoopGroup(new NioEventLoopGroup(1));
	builder.workerEventLoopGroup(new NioEventLoopGroup(100));
	if (isUseTracingInterceptor()) {
	    builder.intercept(getTracingInterceptor());
	}
	return builder.build();
    }

    /*
     * (non-Javadoc)
     * 
     * @see com.sitewhere.server.lifecycle.LifecycleComponent#initialize(com.
     * sitewhere.spi.server.lifecycle.ILifecycleProgressMonitor)
     */
    @Override
    public void initialize(ILifecycleProgressMonitor monitor) throws SiteWhereException {
	try {
	    this.jwtInterceptor = new JwtServerInterceptor(getMicroservice(), getServiceImplementation().getClass());
	    this.tracingInterceptor = new ServerTracingInterceptor(getMicroservice().getTracer());
	    this.server = buildServer();
	    getLogger().debug("Initialized GRPC server on port " + port + ".");
	} catch (Throwable e) {
	    throw new SiteWhereException("Unable to initialize GRPC server.", e);
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sitewhere.server.lifecycle.LifecycleComponent#start(com.sitewhere.spi
     * .server.lifecycle.ILifecycleProgressMonitor)
     */
    @Override
    public void start(ILifecycleProgressMonitor monitor) throws SiteWhereException {
	try {
	    getServer().start();
	    getLogger().debug("Started GRPC server on port " + port + ".");
	} catch (IOException e) {
	    throw new SiteWhereException("Unable to start GRPC server.", e);
	}
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * com.sitewhere.server.lifecycle.LifecycleComponent#stop(com.sitewhere.spi.
     * server.lifecycle.ILifecycleProgressMonitor)
     */
    @Override
    public void stop(ILifecycleProgressMonitor monitor) throws SiteWhereException {
	getServer().shutdown();
	try {
	    getServer().awaitTermination();
	    getLogger().debug("GRPC server terminated successfully.");
	} catch (InterruptedException e) {
	    getLogger().error("Interrupted while waiting for GRPC server to terminate.", e);
	}
    }

    /*
     * @see
     * com.sitewhere.grpc.client.spi.server.IGrpcServer#getServiceImplementation()
     */
    @Override
    public BindableService getServiceImplementation() {
	return serviceImplementation;
    }

    public void setServiceImplementation(BindableService serviceImplementation) {
	this.serviceImplementation = serviceImplementation;
    }

    /*
     * @see
     * com.sitewhere.grpc.client.spi.server.IGrpcServer#isUseTracingInterceptor()
     */
    @Override
    public boolean isUseTracingInterceptor() {
	return useTracingInterceptor;
    }

    public void setUseTracingInterceptor(boolean useTracingInterceptor) {
	this.useTracingInterceptor = useTracingInterceptor;
    }

    /** Used for naming gRPC server executor threads */
    private class GrpcServerThreadFactory implements ThreadFactory {

	/** Counts threads */
	private AtomicInteger counter = new AtomicInteger();

	public Thread newThread(Runnable r) {
	    return new Thread(r, "gRPC Server " + counter.incrementAndGet());
	}
    }

    public Server getServer() {
	return server;
    }

    public void setServer(Server server) {
	this.server = server;
    }

    public int getPort() {
	return port;
    }

    public void setPort(int port) {
	this.port = port;
    }

    public JwtServerInterceptor getJwtInterceptor() {
	return jwtInterceptor;
    }

    public void setJwtInterceptor(JwtServerInterceptor jwtInterceptor) {
	this.jwtInterceptor = jwtInterceptor;
    }

    public ServerTracingInterceptor getTracingInterceptor() {
	return tracingInterceptor;
    }

    public void setTracingInterceptor(ServerTracingInterceptor tracingInterceptor) {
	this.tracingInterceptor = tracingInterceptor;
    }

    public ExecutorService getServerExecutor() {
	return serverExecutor;
    }

    public void setServerExecutor(ExecutorService serverExecutor) {
	this.serverExecutor = serverExecutor;
    }
}