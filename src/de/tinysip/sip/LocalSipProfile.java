/*
 * This file is part of TinySip. 
 * http://code.google.com/p/de-tiny-sip/
 * 
 * Created 2011 by Sebastian Rösch <flowfire@sebastianroesch.de>
 * 
 * This software is licensed under the Apache License 2.0.
 */

package de.tinysip.sip;

import gov.nist.javax.sip.address.SipUri;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.sip.PeerUnavailableException;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.AuthorizationHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderFactory;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.header.ToHeader;

/**
 * Represents the local user's profile, containing his user name, the sip domain and so on. Also contains information about user authorization with the sip provider.
 * 
 * @author Sebastian
 * 
 */
public class LocalSipProfile {
	private String userName;
	private String displayName;
	private String sipDomain;
	private String sipPassword;
	private int sipPort;

	private int localRtpPort;
	private int localRtcpPort;
	private List<SipAudioFormat> audioFormats;

	private String nonce;
	private String realm;

	private String tag;

	/**
	 * Create a LocalSipProfile for the local user, specifying the user name, his sip domain and password.
	 * 
	 * @param userName
	 *            the SIP username of the local user
	 * @param sipDomain
	 *            the SIP domain of the local user
	 * @param sipPassword
	 *            the SIP password
	 */
	public LocalSipProfile(String userName, String sipDomain, String sipPassword) {
		this.userName = userName;
		this.sipDomain = sipDomain;
		this.sipPassword = sipPassword;
		this.sipPort = 5060;
		this.displayName = userName;
		this.audioFormats = new ArrayList<SipAudioFormat>();

		String rand = (Math.random() * 10000) + "";
		this.tag = AuthorizationDigest.getHexString(rand.getBytes());
	}

	/**
	 * Create a LocalSipProfile for the local user, specifying the user name, his sip domain, password and the sip port.
	 * 
	 * @param userName
	 *            the SIP username of the local user
	 * @param sipDomain
	 *            the SIP domain of the local user
	 * @param sipPassword
	 *            the SIP password
	 * @param sipPort
	 *            the provider's SIP port
	 */
	public LocalSipProfile(String userName, String sipDomain, String sipPassword, int sipPort) {
		this(userName, sipDomain, sipPassword);
		this.sipPort = sipPort;
	}

	/**
	 * Create a LocalSipProfile for the local user, specifying the user name, his sip domain, password and display name.
	 * 
	 * @param userName
	 *            the SIP username of the local user
	 * @param sipDomain
	 *            the SIP domain of the local user
	 * @param sipPassword
	 *            the SIP password
	 * @param displayName
	 *            the display name of the local user
	 */
	public LocalSipProfile(String userName, String sipDomain, String sipPassword, String displayName) {
		this(userName, sipDomain, sipPassword);
		this.displayName = displayName;
	}

	/**
	 * Create a LocalSipProfile for the local user, specifying the user name, his sip domain, password sip port and display name.
	 * 
	 * @param userName
	 *            the SIP username of the local user
	 * @param sipDomain
	 *            the SIP domain of the local user
	 * @param sipPassword
	 *            the SIP password
	 * @param sipPort
	 *            the provider's SIP port
	 * @param displayName
	 *            the display name of the local user
	 */
	public LocalSipProfile(String userName, String sipDomain, String sipPassword, int sipPort, String displayName) {
		this(userName, sipDomain, sipPassword);
		this.displayName = displayName;
	}

	/**
	 * Create a javax.sip.header.FromHeader for this LocalSipProfile
	 * 
	 * @param addressFactory
	 *            an instance of an AddressFactory, created by the SipManager
	 * @param headerFactory
	 *            an instance of a HeaderFactory, created by the SipManager
	 * @return the FromHeader for this LocalSipProfile
	 * @throws PeerUnavailableException
	 * @throws ParseException
	 */
	public FromHeader getFromHeader(AddressFactory addressFactory, HeaderFactory headerFactory) throws PeerUnavailableException, ParseException {
		SipURI fromAddress = addressFactory.createSipURI(this.userName, this.sipDomain);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(this.displayName);
		return headerFactory.createFromHeader(fromNameAddress, this.tag);
	}

	/**
	 * Create a javax.sip.header.ToHeader for this LocalSipProfile
	 * 
	 * @param addressFactory
	 *            an instance of an AddressFactory, created by the SipManager
	 * @param headerFactory
	 *            an instance of a HeaderFactory, created by the SipManager
	 * @return the ToHeader created for this LocalSipProfile
	 * @throws PeerUnavailableException
	 * @throws ParseException
	 */
	public ToHeader getToHeader(AddressFactory addressFactory, HeaderFactory headerFactory) throws PeerUnavailableException, ParseException {
		SipURI fromAddress = addressFactory.createSipURI(this.userName, this.sipDomain);
		Address fromNameAddress = addressFactory.createAddress(fromAddress);
		fromNameAddress.setDisplayName(this.displayName);
		return headerFactory.createToHeader(fromNameAddress, null);
	}

	/**
	 * Create a javax.sip.header.AuthorizationHeader for this LocalSipProfile
	 * 
	 * @param headerFactory
	 *            an instance of a HeaderFactory, created by the SipManager
	 * @return the AuthorizationHeader created for this LocalSipProfile
	 * @throws ParseException
	 */
	public AuthorizationHeader getAuthorizationHeader(HeaderFactory headerFactory) throws ParseException {
		SipUri uri = new SipUri();
		uri.setHost(this.sipDomain);

		String responseDigest = AuthorizationDigest.getDigest(this.userName, this.realm, this.sipPassword, "REGISTER", uri.toString(), this.nonce);

		AuthorizationHeader auth = headerFactory.createAuthorizationHeader("Digest");
		auth.setAlgorithm("MD5");
		auth.setNonce(this.nonce);
		auth.setRealm(this.realm);
		auth.setUsername(this.userName);
		auth.setURI(uri);
		auth.setResponse(responseDigest);

		return auth;
	}

	/**
	 * Create a javax.sip.header.ProxyAuthorizationHeader for this LocalSipProfile
	 * 
	 * @param headerFactory
	 *            an instance of a HeaderFactory, created by the SipManager
	 * @param contact
	 *            the SipContact to call
	 * @return the ProxyAuthorizationHeader for this LocalSipProfile
	 * @throws ParseException
	 */
	public ProxyAuthorizationHeader getProxyAuthorizationHeader(HeaderFactory headerFactory, SipContact contact) throws ParseException {
		SipUri uri = new SipUri();
		uri.setHost(this.sipDomain);
		uri.setUser(contact.getSipUserName());

		String responseDigest = AuthorizationDigest.getDigest(this.userName, this.realm, this.sipPassword, "INVITE", uri.toString(), this.nonce);

		ProxyAuthorizationHeader auth = headerFactory.createProxyAuthorizationHeader("Digest");
		auth.setAlgorithm("MD5");
		auth.setNonce(this.nonce);
		auth.setRealm(this.realm);
		auth.setUsername(this.userName);
		auth.setURI(uri);
		auth.setResponse(responseDigest);

		return auth;
	}

	/**
	 * Set the supported audio formats as a list of integers
	 * 
	 * @param audioFormats
	 *            the supported audio formats as a List of SipAudioFormat
	 */
	public void setAudioFormats(List<SipAudioFormat> audioFormats) {
		this.audioFormats = audioFormats;
	}

	/**
	 * @return the supported audio formats as a List of SipAudioFormat
	 */
	public List<SipAudioFormat> getAudioFormats() {
		return audioFormats;
	}

	/**
	 * Set the nonce to use for the authentication
	 * 
	 * @param nonce
	 *            the nonce to use for authentication
	 */
	public void setNonce(String nonce) {
		this.nonce = nonce;
	}

	/**
	 * Set the realm to use for the authentication
	 * 
	 * @param realm
	 *            the realm to use for authentication
	 */
	public void setRealm(String realm) {
		this.realm = realm;
	}

	/**
	 * @return the profile's display name
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * @return the sip domain
	 */
	public String getSipDomain() {
		return sipDomain;
	}

	/**
	 * @return the sip password
	 */
	public String getSipPassword() {
		return sipPassword;
	}

	/**
	 * @return the local sip port
	 */
	public int getSipPort() {
		return sipPort;
	}

	/**
	 * @return the user name
	 */
	public String getUserName() {
		return userName;
	}

	/**
	 * Set the local rtp port.
	 * 
	 * @param localRtpPort
	 */
	public void setLocalRtpPort(int localRtpPort) {
		this.localRtpPort = localRtpPort;
	}

	/**
	 * @return the local rtp port
	 */
	public int getLocalRtpPort() {
		return localRtpPort;
	}

	/**
	 * Set the local rtcp port.
	 * 
	 * @param localRtcpPort
	 */
	public void setLocalRtcpPort(int localRtcpPort) {
		this.localRtcpPort = localRtcpPort;
	}

	/**
	 * @return the local rtcp port
	 */
	public int getLocalRtcpPort() {
		return localRtcpPort;
	}

}
