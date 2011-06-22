/*
 * This file is part of TinySip. 
 * http://code.google.com/p/de-tiny-sip/
 * 
 * Created 2011 by Sebastian Rösch <flowfire@sebastianroesch.de>
 * 
 * This software is licensed under the Apache License 2.0.
 */

package de.tinysip.sip;

import java.net.InetAddress;
import java.text.ParseException;
import java.util.List;

import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.URI;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ToHeader;

/**
 * SipSession contains all information about an ongoing call.
 * 
 * @author Sebastian
 * 
 */
public class SipSession {
	private URI fromSipURI;
	private InetAddress remoteAddress;
	private int remoteSipPort;

	private URI toSipURI;

	private List<SipAudioFormat> audioFormats;
	private int remoteRtpPort;
	private int remoteRtcpPort;

	/**
	 * Create a new SipSession by specifying the caller and the called sip uri
	 * and the remote Internet address.
	 * 
	 * @param fromSipURI the SIP URI of the caller
	 * @param toSipURI the SIP URI of the called
	 * @param remoteAddress the InetAddress od the remote participant
	 */
	public SipSession(URI fromSipURI, URI toSipURI, InetAddress remoteAddress) {
		this.fromSipURI = fromSipURI;
		this.toSipURI = toSipURI;
		this.remoteAddress = remoteAddress;
		this.remoteSipPort = 5060;
	}

	/**
	 * Create a new SipSession by specifying the caller and the called sip uri,
	 * remote Internet address and sip port.
	 * 
	 * @param fromSipURI the SIP URI of the caller
	 * @param toSipURI the SIP URI of the called
	 * @param remoteAddress the InetAddress od the remote participant
	 * @param remoteSipPort the SIP port of the remote participant
	 */
	public SipSession(URI fromSipURI, URI toSipURI, InetAddress remoteAddress, int remoteSipPort) {
		this(fromSipURI, toSipURI, remoteAddress);
		this.remoteSipPort = remoteSipPort;
	}

	/**
	 * Create a new SipSession by specifying the caller and the called sip uri,
	 * remote Internet address, sip port, rtp and rtcp ports.
	 * 
	 * @param fromSipURI the SIP URI of the caller
	 * @param toSipURI the SIP URI of the called
	 * @param remoteAddress the InetAddress od the remote participant
	 * @param remoteSipPort the SIP port of the remote participant
	 * @param remoteRtpPort the RTP port of the remote participant
	 * @param remoteRtcpPort the RTCP port of the remote participant
	 */
	public SipSession(URI fromSipURI, URI toSipURI, InetAddress remoteAddress, int remoteSipPort, int remoteRtpPort, int remoteRtcpPort) {
		this(fromSipURI, toSipURI, remoteAddress, remoteSipPort);
		this.remoteRtpPort = remoteRtpPort;
		this.remoteRtcpPort = remoteRtcpPort;
	}

	/**
	 * Create a javax.sip.header.ToHeader for this SipSession.
	 * 
	 * @param addressFactory an instance of an AddressFactory, created by the SipManager
	 * @param headerFactory an instance of a HeaderFactory, created by the SipManager
	 * @return the ToHeader for this SipSession
	 * @throws ParseException
	 */
	public ToHeader getToHeader(AddressFactory addressFactory, HeaderFactory headerFactory) throws ParseException {
		Address fromNameAddress = addressFactory.createAddress(fromSipURI);
		return headerFactory.createToHeader(fromNameAddress, null);
	}

	/**
	 * Set the supported audio formats for the SipSession.
	 * @param formats a List of SipAudioFormat specifying the supported audio formats of the remote participant
	 */
	public void setAudioFormats(List<SipAudioFormat> audioFormats) {
		this.audioFormats = audioFormats;
	}

	/**
	 * Get the supported audio formats for the SipSessions.
	 * @return formats a List of SipAudioFormat specifying the supported audio formats of the remote participant
	 */
	public List<SipAudioFormat> getAudioFormats() {
		return audioFormats;
	}

	/**
	 * @return the caller number as a formatted string
	 */
	public String getCallerNumber() {
		String result = this.fromSipURI.toString().split("@")[0];
		result = result.replace("sip:0049", "0");
		result = result.replace("sip:+49", "0");
		result = result.replace("sip:49", "0");
		result = result.replace("sip:", "");
		return result;
	}

	/**
	 * @return the javax.sip.address.URI of the caller
	 */
	public URI getFromSipURI() {
		return fromSipURI;
	}

	/**
	 * @return get the remote InetAddress
	 */
	public InetAddress getRemoteAddress() {
		return remoteAddress;
	}

	/**
	 * @return the remote sip port
	 */
	public int getRemotePort() {
		return remoteSipPort;
	}

	/**
	 * @return the remote rtp port
	 */
	public int getRemoteRtpPort() {
		return remoteRtpPort;
	}

	/**
	 * @return the remote rtcp port
	 */
	public int getRemoteRtcpPort() {
		return remoteRtcpPort;
	}

	/**
	 * @return javax.sip.address.URI of the called
	 */
	public URI getToSipURI() {
		return toSipURI;
	}

	@Override
	public boolean equals(Object o) {
		return this.fromSipURI.equals(((SipSession) o).getFromSipURI());
	}

	@Override
	public String toString() {
		return remoteAddress.getHostAddress() + ":" + remoteSipPort + " rtp: " + remoteRtpPort + " rtcp: " + remoteRtcpPort;
	}

}
