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

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.google.api.client.util.Charsets;
import com.google.api.services.samples.youtube.cmdline.data.Input;
import com.google.api.services.samples.youtube.cmdline.data.Output;
import com.google.common.io.Files;
import com.google.gdata.client.media.ResumableGDataFileUploader;
import com.google.gdata.client.uploader.ProgressListener;
import com.google.gdata.client.uploader.ResumableHttpFileUploader;
import com.google.gdata.client.youtube.YouTubeService;
import com.google.gdata.data.media.MediaFileSource;
import com.google.gdata.data.media.mediarss.MediaCategory;
import com.google.gdata.data.media.mediarss.MediaDescription;
import com.google.gdata.data.media.mediarss.MediaKeywords;
import com.google.gdata.data.media.mediarss.MediaTitle;
import com.google.gdata.data.youtube.VideoEntry;
import com.google.gdata.data.youtube.YouTubeMediaGroup;
import com.google.gdata.data.youtube.YouTubeNamespace;
import com.google.gdata.util.ServiceException;

/**
 * Demonstrates YouTube Data API operation to upload large media files.
 *
 * 
 */
public class UploadThread implements Runnable {

	/**
	 * The URL used to resumable upload
	 */
	public static final String RESUMABLE_UPLOAD_URL = "http://uploads.gdata.youtube.com/resumable/feeds/api/users/default/uploads";
	
	private static final String VIDEO_FILE_FORMAT = "video/*";
	
	/** Max size for each upload chunk */
	private static final int DEFAULT_CHUNK_SIZE = 10000000;
	
	public YouTubeService service;
	public Map<String, Input> videoFileNames;
	public File[] descriptionFiles;
	public String inputDescription;
	public List<Output> videoOutputList;
	public String url;
	public List<ResumableGDataFileUploader> uploaders = new ArrayList<ResumableGDataFileUploader>();
	public String youtubeUsername;
	public int delay;

	@Override
	public void run() {

		List<Thread> subThreadList = new ArrayList<Thread>();
		for (String videoFileName : videoFileNames.keySet()) {
			try {
				ResumableGDataFileUploader uploader = prepareVideoUploader(
						service,
						"videos/"+videoFileName, 
						descriptionFiles,
						inputDescription,
						videoFileName.substring(0, videoFileName.length() - 4) // title removed .avi
							.replace("_", " ")
							.replace("-", " "),
						videoOutputList,
						url,
						youtubeUsername);
				Thread subThread = new Thread(new Runnable() {
					
					@Override
					public void run() {
						try {
							uploadVideo(uploader);
							Input input = videoFileNames.get(videoFileName);
							input.status = YouTubeUploadClient.STATUS_PROCESSED;
						} catch (IOException | ServiceException
								| InterruptedException e) {
							e.printStackTrace();
						}
					}
				});
				subThread.start();
				subThreadList.add(subThread);
				System.out.println("Thread " + youtubeUsername 
						+ " sleeping in " + delay + " second(s).");
				Thread.sleep(delay * 1000);
				
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
		}
		for (Thread thread : subThreadList) {
			try {
				thread.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	private static ResumableGDataFileUploader prepareVideoUploader(
			YouTubeService service, 
			String videoFilePath,
			File[] descriptionFiles, 
			String inputDescription, 
			String title,
			List<Output> videoOutputList, 
			String url, 
			String youtubeUsername) throws IOException, ServiceException,
				InterruptedException {
		
		System.out.println("\nProcessing... Email:" 
				+ youtubeUsername +" Video:"+videoFilePath);
		
		File videoFile = new File(videoFilePath);
		if (!videoFile.exists()) {
			System.out.println("Sorry, that video doesn't exist.");
			return null;
		}
		String randomDescription = "";
		if (descriptionFiles.length > 0) {
			final Random random = new Random();
			int randomInt = randomInteger(0, descriptionFiles.length - 1,
					random);
			
			File descriptionFile = descriptionFiles[randomInt];
			randomDescription = Files.toString(descriptionFile, Charsets.UTF_8);
			
			if (randomDescription != null) {
				randomDescription = randomDescription.replaceAll("\\[TITLE\\]",
						title);
				randomDescription = randomDescription.replaceAll("http://url",
						url);
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
		mg.getDescription().setPlainTextContent(
				inputDescription + "\n" + randomDescription);
		
		ResumableGDataFileUploader uploader = new ResumableGDataFileUploader.Builder(
				service, new URL(RESUMABLE_UPLOAD_URL), ms, newEntry)
				.title(videoTitle)
				.chunkSize(DEFAULT_CHUNK_SIZE).build();
		return uploader;
	}
	
	/**
	 * Uploads a new video to YouTube.
	 *
	 * @param service
	 *            An authenticated YouTubeService object.
	 * @throws IOException
	 *             Problems reading user input.
	 */
	private void uploadVideo(ResumableGDataFileUploader uploader) throws IOException, ServiceException,
			InterruptedException {

		uploader.start();
		while (!uploader.isDone()) {
			Thread.sleep(2000);
		}
		switch (uploader.getUploadState()) {
		case COMPLETE:
			VideoEntry entry = uploader.getResponse(VideoEntry.class);
			String videoId = entry.getMediaGroup().getVideoId();
			Output videoOutput = new Output();
			videoOutput.videoId = videoId;
			videoOutput.youtubeAccount = youtubeUsername;
			videoOutput.videoTitle = entry.getTitle().getPlainText();
			YouTubeUploadClient.videoOutputList.add(videoOutput);
			System.out.println("Uploaded successfully! " + videoOutput.videoTitle);
			break;
		case CLIENT_ERROR:
			System.out.println("Upload Failed");
			break;
		default:
			System.out.println("Unexpected upload status");
			break;
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
