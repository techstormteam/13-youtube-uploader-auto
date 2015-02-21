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
import java.util.Random;

import com.google.api.client.util.Charsets;
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

	
	public List<ResumableGDataFileUploader> uploaders = new ArrayList<ResumableGDataFileUploader>();
	public String youtubeUsername;

	@Override
	public void run() {

		for (ResumableGDataFileUploader uploader : uploaders) {
			try {
				uploadVideo(uploader);
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
			Thread.sleep(1000);
		}
		switch (uploader.getUploadState()) {
		case COMPLETE:
			System.out.println("Uploaded successfully");
			VideoEntry entry = uploader.getResponse(VideoEntry.class);
			String videoId = entry.getMediaGroup().getVideoId();
			Output videoOutput = new Output();
			videoOutput.videoId = videoId;
			videoOutput.youtubeAccount = youtubeUsername;
			videoOutput.videoTitle = entry.getTitle().getPlainText();
			System.out.println(videoOutput.videoTitle);
			YouTubeUploadClient.videoOutputList.add(videoOutput);
			break;
		case CLIENT_ERROR:
			System.out.println("Upload Failed");
			break;
		default:
			System.out.println("Unexpected upload status");
			break;
		}
	}
	
	
}
