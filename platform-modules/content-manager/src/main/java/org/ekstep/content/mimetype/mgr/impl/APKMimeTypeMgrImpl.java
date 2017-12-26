package org.ekstep.content.mimetype.mgr.impl;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.BooleanUtils;
import org.ekstep.common.dto.Response;
import org.ekstep.common.logger.PlatformLogger;
import org.ekstep.content.common.ContentOperations;
import org.ekstep.content.mimetype.mgr.IMimeTypeManager;
import org.ekstep.content.pipeline.initializer.InitializePipeline;
import org.ekstep.content.util.AsyncContentOperationUtil;
import org.ekstep.graph.dac.model.Node;
import org.ekstep.learning.common.enums.ContentAPIParams;


/**
 * The Class APKMimeTypeMgrImpl is a implementation of IMimeTypeManager for
 * Mime-Type as <code>application/vnd.android.package-archive</code> or for APK
 * Content.
 * 
 * @author Azhar
 * 
 * @see IMimeTypeManager
 * @see HTMLMimeTypeMgrImpl
 * @see AssetsMimeTypeMgrImpl
 * @see ECMLMimeTypeMgrImpl
 * @see CollectionMimeTypeMgrImpl
 */
public class APKMimeTypeMgrImpl extends BaseMimeTypeManager implements IMimeTypeManager {

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.ekstep.taxonomy.mgr.IMimeTypeManager#upload(org.ekstep.graph.dac.model.
	 * Node, java.io.File, java.lang.String)
	 */
	@Override
	public Response upload(String contentId, Node node, File uploadFile, boolean isAsync) {
		PlatformLogger.log("Uploaded File: " + uploadFile.getName());
		PlatformLogger.log("Calling Upload Content For Node ID: " + node.getIdentifier());
		String[] urlArray = uploadArtifactToAWS(uploadFile, contentId);
		node.getMetadata().put("s3Key", urlArray[0]);
		String fileUrl = urlArray[1];
		node.getMetadata().put(ContentAPIParams.artifactUrl.name(), fileUrl);
		return updateContentNode(node.getIdentifier(), node, fileUrl);
	}
	
	@Override
	public Response upload(String contentId, Node node, String fileUrl) {
		node.getMetadata().put(ContentAPIParams.artifactUrl.name(), fileUrl);
		return updateContentNode(contentId, node, fileUrl);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.ekstep.taxonomy.mgr.IMimeTypeManager#publish(org.ekstep.graph.dac.model
	 * .Node)
	 */
	@Override
	public Response publish(String contentId, Node node, boolean isAsync) {
		PlatformLogger.log("Node: ", node.getIdentifier());

		Response response = new Response();
		PlatformLogger.log("Preparing the Parameter Map for Initializing the Pipeline For Node ID: " + contentId);
		InitializePipeline pipeline = new InitializePipeline(getBasePath(contentId), contentId);
		Map<String, Object> parameterMap = new HashMap<String, Object>();
		parameterMap.put(ContentAPIParams.node.name(), node);
		parameterMap.put(ContentAPIParams.ecmlType.name(), false);

		PlatformLogger.log("Adding 'isPublishOperation' Flag to 'true'");
		parameterMap.put(ContentAPIParams.isPublishOperation.name(), true);

		PlatformLogger.log("Calling the 'Review' Initializer for Node Id: " + contentId);
		response = pipeline.init(ContentAPIParams.review.name(), parameterMap);
		PlatformLogger.log("Review Operation Finished Successfully for Node ID: " , contentId);

		if (BooleanUtils.isTrue(isAsync)) {
			AsyncContentOperationUtil.makeAsyncOperation(ContentOperations.PUBLISH, contentId, parameterMap);
			PlatformLogger.log("Publish Operation Started Successfully in 'Async Mode' for Node Id: " ,contentId);

			response.put(ContentAPIParams.publishStatus.name(),
					"Publish Operation for Content Id '" + contentId + "' Started Successfully!");
		} else {
			PlatformLogger.log("Publish Operation Started Successfully in 'Sync Mode' for Node Id: " , contentId);
			response = pipeline.init(ContentAPIParams.publish.name(), parameterMap);
		}

		return response;

		// PlatformLogger.log("Calling the 'rePublish' for Node ID: " +
		// node.getIdentifier());
		// return rePublish(node);
	}

	@Override
	public Response review(String contentId, Node node, boolean isAsync) {
		PlatformLogger.log("Node: ", node.getIdentifier());

		PlatformLogger.log("Preparing the Parameter Map for Initializing the Pipeline For Node ID: ", node.getIdentifier());
		InitializePipeline pipeline = new InitializePipeline(getBasePath(contentId), contentId);
		Map<String, Object> parameterMap = new HashMap<String, Object>();
		parameterMap.put(ContentAPIParams.node.name(), node);
		parameterMap.put(ContentAPIParams.ecmlType.name(), false);

		PlatformLogger.log("Calling the 'Review' Initializer for Node ID: ", node.getIdentifier());
		return pipeline.init(ContentAPIParams.review.name(), parameterMap);
	}

}
