package com.bc;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Servlet PasswordAsService This was a test project for Brain Corp
 * 
 * @author hongh
 */
public class PasswordAsService extends HttpServlet {

	private static final long serialVersionUID = 1L;
	// Lock for synchronization
	private final static ReentrantReadWriteLock passwdLock = new ReentrantReadWriteLock();
	private final static ReentrantReadWriteLock groupLock = new ReentrantReadWriteLock();

	// Array lists holding information from passwd and group files
	static public ArrayList<String[]> passwdRecords = null;
	static public ArrayList<String[]> groupRecords = null;

	// Header names for passwd and group files
	private final String[] passwdHeaders = { "name", "x", "uid", "gid", "comment", "home", "shell" };
	private final String[] groupHeaders = { "name", "x", "gid", "members" };

	/**
	 * @see HttpServlet#HttpServlet()
	 */
	public PasswordAsService() {
		super();
	}

	@Override
	public void init() {

		// Start refresh thread for files
		this.syncFiles();

	}

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response)
			throws ServletException, java.io.IOException {

		// Define String for response content
		String responseStr = "";
		JSONArray responseJSONArray = new JSONArray();

		try {
			// Flag for 404 error
			boolean flag404 = false;

			// Get request uri
			String uri = URLDecoder.decode(request.getRequestURI(), "UTF-8");

			// Remove application context
			uri = uri.replace(request.getContextPath(), "");

			// Parse URI and determine use case number
			int usecaseNumber = parseURI(uri);

			// JSONArrays holding query results
			JSONArray passwdJSONArray = null;
			JSONArray groupJSONArray = null;

			switch (usecaseNumber) {
			case -1: // URL not found, send 404
				flag404 = true;
				break;
			case 1: // Find all users
				passwdJSONArray = getUsers();
				responseJSONArray = passwdJSONArray;
				break;

			case 2: // Query users by fields

				// Get parameters
				String name = request.getParameter("name");
				String uid = request.getParameter("uid");
				String gid = request.getParameter("gid");
				String comment = request.getParameter("comment");
				String home = request.getParameter("home");
				String shell = request.getParameter("shell");

				passwdJSONArray = getUsers(name, uid, gid, comment, home, shell);
				responseJSONArray = passwdJSONArray;
				break;

			case 3: // Query user by uid

				// Get uid
				Pattern pattern = Pattern.compile("[0-9]+");
				Matcher matcher = pattern.matcher(uri);

				flag404 = true;

				if (matcher.find()) {
					uid = matcher.group();

					passwdJSONArray = getUser(uid);
					if (!passwdJSONArray.isEmpty())
						flag404 = false;
				}
				responseJSONArray = passwdJSONArray;
				break;

			case 4: // Query all the groups for a given user uid

				// Get uid
				pattern = Pattern.compile("[0-9]+");
				matcher = pattern.matcher(uri);

				if (matcher.find()) {
					uid = matcher.group();

					// Find name of the user
					passwdJSONArray = getUser(uid);

					String userName = (String) (((JSONObject) passwdJSONArray.get(0)).get("name"));

					// Use name to find groups
					groupJSONArray = getGroups(userName);

					// Fill matching groups into groupJSONArray
					responseJSONArray = groupJSONArray;

				}

				break;
			case 5: // Get all groups

				groupJSONArray = getGroups();
				responseJSONArray = groupJSONArray;

				break;
			case 6: // Query groups by fields

				// Get parameters
				name = request.getParameter("name");
				gid = request.getParameter("gid");
				String[] members = request.getParameterValues("member");

				groupJSONArray = getGroups(name, gid, members);
				responseJSONArray = groupJSONArray;

				break;
			case 7: // Use gid to find a group

				// Get gid
				pattern = Pattern.compile("[0-9]+");
				matcher = pattern.matcher(uri);

				flag404 = true;

				if (matcher.find()) {
					gid = matcher.group();

					groupJSONArray = getGroup(gid);
					if (!groupJSONArray.isEmpty())
						flag404 = false;
				}
				responseJSONArray = groupJSONArray;

				break;
			} // Switch cases

			// If flag404 is set send 404
			if (flag404) {
				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			} else {
				// Generate response String
				if (responseJSONArray.isEmpty()) {
					responseStr = "No matching records found";
				} else {
					responseStr = responseJSONArray.toJSONString();
				}

				response.setContentType("text/plain");
				response.setCharacterEncoding("UTF-8");
				PrintWriter responseWriter = response.getWriter();

				responseWriter.println(responseStr);
				responseWriter.close();
			}
		} catch (

		Exception e) { // Print stack trace for unhandled exceptions
			response.setContentType("text/plain");
			response.setCharacterEncoding("UTF-8");
			PrintWriter responseWriter = response.getWriter();

			e.printStackTrace(responseWriter);
			responseWriter.close();

		}

	} // End of doGet

	/**
	 * @param uid:
	 *            String carrying uid
	 * @return: JSONArray with results
	 */
	@SuppressWarnings("unchecked")
	private JSONArray getUser(String uid) {
		// Define return JSONArray
		JSONArray passwdJSONArray = new JSONArray();

		// set read lock
		passwdLock.readLock().lock();

		// Copy passwdRecords to local
		ArrayList<String[]> passwdRecords = PasswordAsService.passwdRecords;

		// Release read lock
		passwdLock.readLock().unlock();

		// Search through passwdRecords
		for (int i = 0; i < passwdRecords.size(); i++) {

			String[] rowStringArray = passwdRecords.get(i);

			if (!uid.equals(rowStringArray[2]))
				continue; // Stop when finding the uid - assuming uid is unique

			JSONObject rowJSON = new JSONObject();

			// Assemble fields into JSONObject
			for (int j = 0; j < 7; j++) {

				if (j == 1)
					continue;
				rowJSON.put(passwdHeaders[j], rowStringArray[j]);

			}
			passwdJSONArray.add(rowJSON);

		}

		return passwdJSONArray;
	}

	/**
	 * @param name:
	 *            String carrying user name
	 * @param uid:
	 *            String carrying uid
	 * @param gid:
	 *            String carring gid of user's primary group
	 * @param comment:
	 *            String carrying comment for the user
	 * @param home:
	 *            String carrying home directory of the user
	 * @param shell:
	 *            String carrying shell for the user
	 * @return: JSONArray with results
	 */
	@SuppressWarnings("unchecked")
	private JSONArray getUsers(String name, String uid, String gid, String comment, String home, String shell) {

		// Define return JSONArray
		JSONArray passwdJSONArray = new JSONArray();

		// set read lock
		passwdLock.readLock().lock();

		// Copy passwdRecords to local
		ArrayList<String[]> passwdRecords = PasswordAsService.passwdRecords;

		// Release read lock
		passwdLock.readLock().unlock();

		// Search through passwdRecords
		for (int i = 0; i < passwdRecords.size(); i++) {

			String[] rowStringArray = passwdRecords.get(i);

			if (name != null) {
				if (!name.equals(rowStringArray[0]))
					continue;
			}
			if (uid != null) {
				if (!uid.equals(rowStringArray[2]))
					continue;
			}
			if (gid != null) {
				if (!gid.equals(rowStringArray[3]))
					continue;
			}
			if (comment != null) {
				if (!comment.equals(rowStringArray[4]))
					continue;
			}
			if (home != null) {
				if (!home.equals(rowStringArray[5]))
					continue;
			}
			if (shell != null) {
				if (!shell.equals(rowStringArray[6]))
					continue;
			}

			// Assemble records in a row into JSONObject
			JSONObject rowJSON = new JSONObject();

			for (int j = 0; j < 7; j++) {

				if (j == 1)
					continue;
				rowJSON.put(passwdHeaders[j], rowStringArray[j]);

			}
			passwdJSONArray.add(rowJSON);
		}
		return passwdJSONArray;

	}

	/**
	 * @return: JSONArray with all users in passwd file
	 */
	@SuppressWarnings("unchecked")
	private JSONArray getUsers() {
		// Define return JSONArray
		JSONArray passwdJSONArray = new JSONArray();

		// set read lock
		passwdLock.readLock().lock();

		// Copy passwdRecords to local
		ArrayList<String[]> passwdRecords = PasswordAsService.passwdRecords;

		// Release read lock
		passwdLock.readLock().unlock();

		for (int i = 0; i < passwdRecords.size(); i++) {

			JSONObject rowJSON = new JSONObject();

			for (int j = 0; j < 7; j++) {

				if (j == 1)
					continue;
				rowJSON.put(passwdHeaders[j], passwdRecords.get(i)[j]);

			}
			passwdJSONArray.add(rowJSON);
		}

		return passwdJSONArray;
	}

	/**
	 * @param userName:
	 *            String carrying name of the user
	 * @return: JSONArray with results of every group that the user is part of
	 */
	@SuppressWarnings("unchecked")
	private JSONArray getGroups(String userName) {
		// Define return JSONArray
		JSONArray groupJSONArray = new JSONArray();

		// set read lock
		groupLock.readLock().lock();

		// Copy passwdRecords to local
		ArrayList<String[]> groupRecords = PasswordAsService.groupRecords;

		// Release read lock
		groupLock.readLock().unlock();

		// Search through passwdRecords
		for (int i = 0; i < groupRecords.size(); i++) {

			String[] rowStringArray = groupRecords.get(i);

			if (rowStringArray.length < 4)
				continue;

			String[] groupMembers = rowStringArray[3].split(",");

			// Find members
			boolean memberMatch = false;
			for (int j = 0; j < groupMembers.length; j++) {

				if (groupMembers[j].equals(userName)) {

					memberMatch = true;
				}

			}

			if (memberMatch) {
				// Assemble fields into JSONObject
				JSONObject rowJSON = new JSONObject();

				// Add group name and gid
				for (int j = 0; j < 3; j++) {

					if (j == 1)
						continue;
					rowJSON.put(groupHeaders[j], rowStringArray[j]);

				}

				// Put members
				JSONArray membersJSONArray = new JSONArray();

				for (int j = 0; j < groupMembers.length; j++) {

					membersJSONArray.add(groupMembers[j]);
				}

				rowJSON.put("members", membersJSONArray);

				groupJSONArray.add(rowJSON);
			}
		}

		return groupJSONArray;
	}

	/**
	 * @return: JSONArray with all groups in file /etc/group
	 */
	@SuppressWarnings("unchecked")
	private JSONArray getGroups() {
		// Define return JSONArray
		JSONArray groupJSONArray = new JSONArray();

		// set read lock
		groupLock.readLock().lock();

		// Copy passwdRecords to local
		ArrayList<String[]> groupRecords = PasswordAsService.groupRecords;

		// Release read lock
		groupLock.readLock().unlock();

		// Search through passwdRecords
		for (int i = 0; i < groupRecords.size(); i++) {

			String[] rowStringArray = groupRecords.get(i);

			// Assemble fields into JSONObject
			JSONObject rowJSON = new JSONObject();

			// Add group name and gid
			for (int j = 0; j < 3; j++) {

				if (j == 1)
					continue;
				rowJSON.put(groupHeaders[j], rowStringArray[j]);
			}

			// Add members as JSONArray, which can be empty
			JSONArray membersJSONArray = new JSONArray();
			if (rowStringArray.length >= 4) { // If group has members

				String[] groupMembers = rowStringArray[3].split(",");

				// Put members

				for (int j = 0; j < groupMembers.length; j++) {

					membersJSONArray.add(groupMembers[j]);
				}

			}
			rowJSON.put("members", membersJSONArray);

			groupJSONArray.add(rowJSON);
		}

		return groupJSONArray;

	}

	/**
	 * @param name:
	 *            String carrying name of group
	 * @param gid:
	 *            String carrying gid
	 * @param members:
	 *            String array carrying member(s)
	 * @return: JSONArray with result that matches all query parameters
	 */
	@SuppressWarnings({ "unchecked" })
	private JSONArray getGroups(String name, String gid, String[] members) {

		// Define return JSONArray
		JSONArray groupJSONArray = new JSONArray();

		// set read lock
		groupLock.readLock().lock();

		// Copy passwdRecords to local
		ArrayList<String[]> groupRecords = PasswordAsService.groupRecords;

		// Release read lock
		groupLock.readLock().unlock();

		// Search through passwdRecords
		for (int i = 0; i < groupRecords.size(); i++) {

			String[] rowStringArray = groupRecords.get(i);
			String[] groupMembers = null;
			JSONArray membersJSONArray = new JSONArray();

			if (name != null) {
				if (!name.equals(rowStringArray[0]))
					continue;
			}
			if (gid != null) {
				if (!gid.equals(rowStringArray[2]))
					continue;
			}
			if (members != null) {

				// If group does not have members, skip
				if (rowStringArray.length < 4) {
					continue;
				} else {
					groupMembers = rowStringArray[3].split(",");

					// Find members
					boolean memberMatch = false;
					for (int k = 0; k < members.length; k++) { // Loop through
																// members
																// values
						memberMatch = false; // reset flag
						for (int j = 0; j < groupMembers.length; j++) { // Loop
																		// through
																		// members
																		// in
																		// group

							if (groupMembers[j].equals(members[k])) {

								memberMatch = true;
								break;
							}

						}
						if (!memberMatch)
							break;

					}
					if (!memberMatch)
						continue;

				} // When group has members
			} // When query members field is not empty

			// Assemble records in a row into JSONObject
			JSONObject rowJSON = new JSONObject();

			// Add group name and gid
			for (int j = 0; j < 3; j++) {

				if (j == 1)
					continue;
				rowJSON.put(groupHeaders[j], rowStringArray[j]);

			}

			// Put members if groupMembers is not null
			if (groupMembers != null) {

				for (int j = 0; j < groupMembers.length; j++) {

					membersJSONArray.add(groupMembers[j]);
				}

			}

			rowJSON.put("members", membersJSONArray);
			groupJSONArray.add(rowJSON);
		} // Loop through all groups
		return groupJSONArray;

	}

	/**
	 * @param gid:
	 *            String carrying gid
	 * @return: JSONArray with result that matches given gid
	 */
	@SuppressWarnings({ "unchecked" })
	private JSONArray getGroup(String gid) {

		// Define return JSONArray
		JSONArray groupJSONArray = new JSONArray();

		// set read lock
		groupLock.readLock().lock();

		// Copy passwdRecords to local
		ArrayList<String[]> groupRecords = PasswordAsService.groupRecords;

		// Release read lock
		groupLock.readLock().unlock();

		// Search through passwdRecords
		for (int i = 0; i < groupRecords.size(); i++) {

			String[] rowStringArray = groupRecords.get(i);
			String[] groupMembers = null;
			JSONArray membersJSONArray = new JSONArray();

			if (gid.equals(rowStringArray[2])) {

				// Assemble records in a row into JSONObject
				JSONObject rowJSON = new JSONObject();

				// Add group name and gid
				for (int j = 0; j < 3; j++) {

					if (j == 1)
						continue;
					rowJSON.put(groupHeaders[j], rowStringArray[j]);

				}

				// Put members if groupMembers is not null
				if (rowStringArray.length >= 4) {
					groupMembers = rowStringArray[3].split(",");

					for (int j = 0; j < groupMembers.length; j++) {

						membersJSONArray.add(groupMembers[j]);
					}

				}

				rowJSON.put("members", membersJSONArray);
				groupJSONArray.add(rowJSON);

				break;
			}
		} // Loop through all groups
		return groupJSONArray;

	}

	/**
	 * @param uri:
	 *            URL from HTTP request
	 * @return: Integer representing use case. -1 if no match is found
	 */
	private int parseURI(String uri) {
		if (uri.equals("/users")) { // First use case
			return 1;
		} else if (uri.equals("/users/query")) { // 2nd use case
			return 2;
		} else if (uri.matches("/users/[0-9]+")) { // 3rd use case
			return 3;
		} else if (uri.matches("/users/[0-9]+/groups")) { // 4th use case
			return 4;
		} else if (uri.equals("/groups")) { // 5th use case
			return 5;
		} else if (uri.equals("/groups/query")) { // 6th use case
			return 6;
		} else if (uri.matches("/groups/[0-9]+")) { // 7th use case
			return 7;
		}

		return -1; // if URI can not be matched
	}

	/**
	 * A thread that refreshes /etc/passwd and /etc/group every "refresh_time"
	 * seconds It establishes write lock to synchronize with other threads
	 */
	private void syncFiles() {

		// get file paths and refresh time from web.xml
		final String passwdFilePath = getServletContext().getInitParameter("passwd_path");
		final String groupFilePath = getServletContext().getInitParameter("group_path");
		final String refreshSeconds = getServletContext().getInitParameter("refresh_time");

		Thread passwdThread = new Thread() {
			String path1 = passwdFilePath;
			String path2 = groupFilePath;
			long lastPasswdTimestamp = -1;
			long lastGroupTimestamp = -1;

			@Override
			public void run() {
				try {
					while (true) {

						// Get passwd file timestamp
						long passwdTimestamp = new File(path1).lastModified();

						// Check if passwd file has changed)
						if (passwdTimestamp != lastPasswdTimestamp) {

							// Read passwd file
							String passwdFileStr = readFile(path1);

							// Parse passwd into variables
							String[] passwdLines = passwdFileStr.split("\n");

							ArrayList<String[]> passwdRecords = new ArrayList<String[]>();

							for (int i = 0; i < passwdLines.length; i++) {

								String[] passwdRecordRow = passwdLines[i].split(":");

								if (passwdRecordRow.length != 7)
									throw new Exception("Passwd file corrupted");
								passwdRecords.add(passwdRecordRow);

							}

							// Set write lock
							passwdLock.writeLock().lock();

							// Write variable
							PasswordAsService.passwdRecords = passwdRecords;

							// Release write lock
							passwdLock.writeLock().unlock();

							lastPasswdTimestamp = passwdTimestamp;

							System.out.println("Passwd file updated");
						}

						// Get group file timestamp
						long groupTimestamp = new File(path2).lastModified();

						// Check if group file has changed
						if (groupTimestamp != lastGroupTimestamp) {

							// Read group file
							String groupFileStr = readFile(path2);

							// Parse group into variables
							String[] groupLines = groupFileStr.split("\n");

							ArrayList<String[]> groupRecords = new ArrayList<String[]>();

							for (int i = 0; i < groupLines.length; i++) {
								String[] groupRecordRow = groupLines[i].split(":");
								if (groupRecordRow.length < 3 || groupRecordRow.length > 4)
									throw new Exception("group file corrupted");

								groupRecords.add(groupRecordRow);
							}

							// Set write lock
							groupLock.writeLock().lock();

							// Write variable
							PasswordAsService.groupRecords = groupRecords;

							// Release write lock
							groupLock.writeLock().unlock();

							lastGroupTimestamp = groupTimestamp;
							
							System.out.println("Group file updated");

						}	// Check and update group

						Thread.sleep(Long.parseLong(refreshSeconds) * 1000);
					}

				} catch (Exception v) {
					System.out.println(v);
				}
			}
		};

		passwdThread.start();
	}

	/**
	 * @param path:
	 *            String carrying path of the file to be read
	 * @return: String with all file contents
	 * @throws IOException
	 */
	static String readFile(String path) throws IOException {
		byte[] encoded = Files.readAllBytes(Paths.get(path));
		return new String(encoded);
	}
}
