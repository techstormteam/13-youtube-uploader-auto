/* Copyright (c) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sample.youtube;

import au.com.bytecode.opencsv.CSV;
import au.com.bytecode.opencsv.CSVReadProc;
import au.com.bytecode.opencsv.CSVWriteProc;
import au.com.bytecode.opencsv.CSVWriter;

import com.google.api.client.googleapis.auth.clientlogin.ClientLogin.Response;
import com.google.api.client.util.Charsets;
import com.google.api.services.samples.youtube.cmdline.Auth;
import com.google.api.services.samples.youtube.cmdline.data.Account;
import com.google.common.io.Files;
import com.google.gdata.client.media.ResumableGDataFileUploader;

import sample.util.SimpleCommandLineParser;

import com.google.gdata.client.youtube.YouTubeService;
import com.google.gdata.data.media.MediaFileSource;
import com.google.gdata.data.media.mediarss.MediaCategory;
import com.google.gdata.data.media.mediarss.MediaDescription;
import com.google.gdata.data.media.mediarss.MediaKeywords;
import com.google.gdata.data.media.mediarss.MediaTitle;
import com.google.gdata.data.youtube.VideoEntry;
import com.google.gdata.data.youtube.YouTubeMediaGroup;
import com.google.gdata.data.youtube.YouTubeNamespace;
import com.google.gdata.util.AuthenticationException;
import com.google.gdata.util.ServiceException;
import com.google.gdata.client.uploader.ProgressListener;
import com.google.gdata.client.uploader.ResumableHttpFileUploader;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Demonstrates YouTube Data API operation to upload large media files.
 *
 * 
 */
public class YouTubeUploadClient {

	/**
	 * The URL used to resumable upload
	 */
	public static final String RESUMABLE_UPLOAD_URL = "http://uploads.gdata.youtube.com/resumable/feeds/api/users/default/uploads";

	private static final String VIDEO_FILE_FORMAT = "video/*";
	
	/** Time interval at which upload task will notify about the progress */
	private static final int PROGRESS_UPDATE_INTERVAL = 3000;

	/** Max size for each upload chunk */
	private static final int DEFAULT_CHUNK_SIZE = 10000000;

	/** Steam to print status messages to. */
	PrintStream output;

	/**
	 * Input stream for reading user input.
	 */
	private static final BufferedReader bufferedReader = new BufferedReader(
			new InputStreamReader(System.in));

	/**
	 * A {@link ProgressListener} implementation to track upload progress. The
	 * listener can track multiple uploads at the same time.
	 */
	private class FileUploadProgressListener implements ProgressListener {
		public synchronized void progressChanged(
				ResumableHttpFileUploader uploader) {
			switch (uploader.getUploadState()) {
			case COMPLETE:
				output.println("Upload Completed");
				break;
			case CLIENT_ERROR:
				output.println("Upload Failed");
				break;
			case IN_PROGRESS:
				output.println(String.format("%3.0f",
						uploader.getProgress() * 100) + "%");
				break;
			case NOT_STARTED:
				output.println("Upload Not Started");
				break;
			}
		}
	}

	private YouTubeUploadClient(PrintStream out) {
		this.output = out;
	}

	/**
	 * Uploads a new video to YouTube.
	 *
	 * @param service
	 *            An authenticated YouTubeService object.
	 * @throws IOException
	 *             Problems reading user input.
	 */
	private void uploadVideo(YouTubeService service, 
			String videoFilePath,
			File[] descriptionFiles, String title, 
			List<String> videoIdList, String url) throws IOException,
			ServiceException, InterruptedException {

		System.out.println(videoFilePath);
		File videoFile = new File(videoFilePath);
		if (!videoFile.exists()) {
			output.println("Sorry, that video doesn't exist.");
			return;
		}

		String randomDescription = "";
        if (descriptionFiles.length > 0) {
        	final Random random = new Random();
            int randomInt = randomInteger(0, descriptionFiles.length - 1, random);
            File descriptionFile = descriptionFiles[randomInt];
            randomDescription = Files.toString(descriptionFile, Charsets.UTF_8);
            if (randomDescription != null) {
            	randomDescription = randomDescription.replaceAll("\\[TITLE\\]", title);
            	randomDescription = randomDescription.replaceAll("http://url", url);
            }
        }
		
		MediaFileSource ms = new MediaFileSource(videoFile, VIDEO_FILE_FORMAT);

		String videoTitle = title;

		VideoEntry newEntry = new VideoEntry();
		YouTubeMediaGroup mg = newEntry.getOrCreateMediaGroup();
		mg.addCategory(new MediaCategory(YouTubeNamespace.CATEGORY_SCHEME,
				"Tech"));
		mg.setTitle(new MediaTitle());
		mg.getTitle().setPlainTextContent(videoTitle);
		mg.setKeywords(new MediaKeywords());
		mg.getKeywords().addKeyword("gdata-test");
		mg.setDescription(new MediaDescription());
		mg.getDescription().setPlainTextContent(randomDescription);

		FileUploadProgressListener listener = new FileUploadProgressListener();
		ResumableGDataFileUploader uploader = new ResumableGDataFileUploader.Builder(
				service, new URL(RESUMABLE_UPLOAD_URL), ms, newEntry)
				.title(videoTitle)
				.trackProgress(listener, PROGRESS_UPDATE_INTERVAL)
				.chunkSize(DEFAULT_CHUNK_SIZE).build();

		uploader.start();
		while (!uploader.isDone()) {
			Thread.sleep(PROGRESS_UPDATE_INTERVAL);
		}

		switch (uploader.getUploadState()) {
		case COMPLETE:
			output.println("Uploaded successfully");
			VideoEntry entry = uploader.getResponse(VideoEntry.class);
            String videoId = entry.getMediaGroup().getVideoId();
            videoIdList.add(videoId);
			break;
		case CLIENT_ERROR:
			output.println("Upload Failed");
			break;
		default:
			output.println("Unexpected upload status");
			break;
		}
	}

	private static int accountIndex = 0;
	private static int videoNumberPerAccount = 0;
	private static boolean newAccount = true;
	private static int temp;
	
	/**
	 * YouTubeUploadClient is a sample command line application that
	 * demonstrates how to upload large media files to youtube. This sample uses
	 * resumable upload feature to upload large media.
	 *
	 * @param args
	 *            Used to pass the username and password of a test account.
	 */
	public static void main(String[] args) {
		String tempFirst = "input.csv";
		String tempSecond = "description";
		String tempThird = "account.csv";

		final String first = tempFirst;
		final String second = tempSecond;
		final String third = tempThird; // number of video each account
		final String output = "output.txt";
		
		SimpleCommandLineParser parser = new SimpleCommandLineParser(args);
		videoNumberPerAccount = 0;
		try {
			videoNumberPerAccount = Integer.parseInt(parser.getValue("videonumber", "h"));
		} catch (NumberFormatException e) {
			videoNumberPerAccount = 0;
		}
		boolean help = parser.containsKey("help", "h");
		String developerKey = "AIzaSyDx4EOP-SO8KM6pCXGOi9D7lv3a4X4S-6g"; //vinh.thien0301@gmail.com
		

		if (help || videoNumberPerAccount == 0) {
			printUsage();
			System.exit(1);
		}

		YouTubeService service = new YouTubeService(
				"gdataSample-YouTubeAuth-1", developerKey);

		YouTubeUploadClient client = new YouTubeUploadClient(System.out);
		
		File directory = new File(second);
		final File[] descriptionFiles = directory.listFiles();

		final List<String> videoIdList = new ArrayList<String>();
		final List<Account> accountList = new ArrayList<Account>();

		// read csv file for each rows
		CSV csv = CSV.separator(',') // delimiter of fields
				.create(); // new instance is immutable

		csv.read(third, new CSVReadProc() {
			public void procRow(int rowIndex, String... values) {
				if (rowIndex == 0) {
					return;
				}

				Account acc = new Account();
				acc.username = values[0];
				acc.password = values[1];
				accountList.add(acc);
			}
		});

		if (accountList.size() == 0) {
			System.out.println("Account not found.");
			System.out.println("Stopped uploading videos.");
			return;
		}

		accountIndex = 0;

		csv.read(first, new CSVReadProc() {
			public void procRow(int rowIndex, String... values) {
				if (rowIndex == 0) {
					return;
				}
				
				if (accountList.size() <= accountIndex) {
					accountIndex = accountList.size() - 1;
				}
				
				System.out.println("\nProcessing... Email:" 
						+ accountList.get(accountIndex).username +" Password:"
						+ accountList.get(accountIndex).password+" Video:"+values[0]);
				
				try {
						service.setUserCredentials(accountList.get(accountIndex).username, 
								accountList.get(accountIndex).password);
				} catch (AuthenticationException e) {
					System.out.println("Invalid login credentials.");
					System.out.println(e.getMessage());
					System.exit(1);
				}
				

				try {
					client.uploadVideo(
							service, // YouTubeService
							"videos/"+values[0], // videoFilePath
							descriptionFiles, // descriptionFiles
							values[0], // title
							videoIdList, // videoIdList
							values[2]); // url

				} catch (IOException e) {
					// Communications error
					System.err
							.println("There was a problem communicating with the service.");
					e.printStackTrace();
				} catch (ServiceException se) {
					System.out.println("Sorry, your upload was invalid:");
					System.out.println(se.getResponseBody());
					se.printStackTrace();
				} catch (InterruptedException ie) {
					System.out.println("Upload interrupted");
				}
				
				temp = accountIndex;
				accountIndex = rowIndex / videoNumberPerAccount;
				
			}
		});
		csv.write(output, new CSVWriteProc() {
			public void process(CSVWriter out) {
				for (String id : videoIdList) {
					out.writeNext("https://www.youtube.com/watch?v=" + id);
				}
			}
		});
		System.exit(0);
	}

	/**
	 * Shows the usage of how to run the sample from the command-line.
	 */
	private static void printUsage() {
		System.out.println("Usage: java YouTubeUploadClient.jar "
				+ " --videonumber <video number each account can upload>");
	}

	/**
	 * Displays a menu of the main activities a user can perform.
	 */
	private static void printMenu() {
		System.out.println("\n");
		System.out.println("Choose one of the following demo options:");
		System.out.println("\t1) Upload new video");
		System.out.println("\t0) Exit");
	}

	/**
	 * Reads a line of text from the standard input.
	 *
	 * @throws IOException
	 *             If unable to read a line from the standard input.
	 * @return A line of text read from the standard input.
	 */
	private static String readLine() throws IOException {
		return bufferedReader.readLine();
	}

	/**
	 * Reads a line of text from the standard input and returns the parsed
	 * integer representation.
	 *
	 * @throws IOException
	 *             If unable to read a line from the standard input.
	 * @return An integer read from the standard input.
	 */
	private static int readInt() throws IOException {
		String input = readLine();

		try {
			return Integer.parseInt(input);
		} catch (NumberFormatException nfe) {
			return 0;
		}

	}
	
	private static int randomInteger(int aStart, int aEnd, Random aRandom){
        if (aStart > aEnd) {
          throw new IllegalArgumentException("Start cannot exceed End.");
        }
        //get the range, casting to long to avoid overflow problems
        long range = (long)aEnd - (long)aStart + 1;
        // compute a fraction of the range, 0 <= frac < range
        long fraction = (long)(range * aRandom.nextDouble());
        int randomNumber =  (int)(fraction + aStart);
        return randomNumber;
      }

}
