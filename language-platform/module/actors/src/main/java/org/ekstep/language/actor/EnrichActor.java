package org.ekstep.language.actor;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ekstep.language.common.LanguageBaseActor;
import org.ekstep.language.common.enums.LanguageActorNames;
import org.ekstep.language.common.enums.LanguageErrorCodes;
import org.ekstep.language.common.enums.LanguageOperations;
import org.ekstep.language.common.enums.LanguageParams;
import org.ekstep.language.measures.entity.WordComplexity;
import org.ekstep.language.measures.meta.SyllableMap;
import org.ekstep.language.util.ControllerUtil;
import org.ekstep.language.util.WordUtil;
import org.ekstep.language.util.WordnetUtil;

import com.ilimi.common.dto.Request;
import com.ilimi.common.dto.Response;
import com.ilimi.common.exception.ClientException;
import com.ilimi.common.exception.ServerException;
import com.ilimi.graph.common.enums.GraphHeaderParams;
import com.ilimi.graph.dac.enums.GraphDACParams;
import com.ilimi.graph.dac.enums.RelationTypes;
import com.ilimi.graph.dac.model.Node;
import com.ilimi.graph.dac.model.Relation;
import com.ilimi.graph.engine.router.GraphEngineManagers;

import akka.actor.ActorRef;

public class EnrichActor extends LanguageBaseActor {

	private static Logger LOGGER = LogManager.getLogger(EnrichActor.class.getName());
	private ControllerUtil controllerUtil = new ControllerUtil();
	private final int BATCH_SIZE = 10000;
	
	private WordUtil wordUtil = new WordUtil();
	
	@SuppressWarnings("unchecked")
	@Override
	public void onReceive(Object msg) throws Exception {
		LOGGER.info("Received Command: " + msg);
		Request request = (Request) msg;
		String languageId = (String) request.getContext().get(LanguageParams.language_id.name());
		String operation = request.getOperation();
		try {
			if (StringUtils.equalsIgnoreCase(LanguageOperations.updateLexileMeasures.name(), operation)) {
				List<Node> nodeList = (List<Node>) request.get(LanguageParams.node_list.name());
				updateLexileMeasures(languageId, nodeList);
				OK(getSender());
			} else if (StringUtils.equalsIgnoreCase(LanguageOperations.updateFrequencyCount.name(), operation)) {
				List<Node> nodeList = (List<Node>) request.get(LanguageParams.node_list.name());
				updateFrequencyCount(languageId, nodeList);
				OK(getSender());
			} else if (StringUtils.equalsIgnoreCase(LanguageOperations.updatePosList.name(), operation)) {
                List<Node> nodeList = (List<Node>) request.get(LanguageParams.node_list.name());
                updatePosList(languageId, nodeList);
                OK(getSender());
            } else if (StringUtils.equalsIgnoreCase(LanguageOperations.enrichWords.name(), operation)) {
				List<String> nodeIds = (List<String>) request.get(LanguageParams.node_ids.name());
				enrichWords(nodeIds, languageId);
				OK(getSender());
			} else if (StringUtils.equalsIgnoreCase(LanguageOperations.importDataAsync.name(), operation)) {
				InputStream stream = (InputStream) request.get(LanguageParams.input_stream.name());
				String prevTaskId = (request.get(LanguageParams.prev_task_id.name()) == null) ? null
						: (String) request.get(LanguageParams.prev_task_id.name());
				if (prevTaskId != null) {
					if (controllerUtil.taskCompleted(prevTaskId, languageId)) {
						controllerUtil.importNodesFromStreamAsync(stream, languageId);
					}
				} else {
					controllerUtil.importNodesFromStreamAsync(stream, languageId);
				}
				OK(getSender());
			} else {
				LOGGER.info("Unsupported operation: " + operation);
				throw new ClientException(LanguageErrorCodes.ERR_INVALID_OPERATION.name(),
						"Unsupported operation: " + operation);
			}
		} catch (Exception e) {
		    System.out.println("Error: " + e.getMessage());
		    LOGGER.error("Error in enrich actor", e);
			handleException(e, getSender());
		}
	}

	private void enrichWords(List<String> node_ids, String languageId) {
		if (null != node_ids && !node_ids.isEmpty()) {
		    Set<String> nodeIds = new HashSet<String>();
		    nodeIds.addAll(node_ids);
		    ArrayList<String> batch_node_ids = new ArrayList<String>();
	        int count = 0;
		    for (String nodeId : nodeIds) {
	            count++;
	            batch_node_ids.add(nodeId);
	            if (batch_node_ids.size() % BATCH_SIZE == 0 || (nodeIds.size() % BATCH_SIZE == batch_node_ids.size() && (nodeIds.size() - count) < BATCH_SIZE)) {
	                long startTime = System.currentTimeMillis();
	                List<Node> nodeList = getNodesList(batch_node_ids, languageId);
	                if(languageId.equalsIgnoreCase("en")){
	                    updateSyllablesList(nodeList);
	                }
	                updateLexileMeasures(languageId, nodeList);
	                updateFrequencyCount(languageId, nodeList);
	                updatePosList(languageId, nodeList);
	                updateWordComplexity(languageId, nodeList);

	                //updateWordChainRelations();
	                batch_node_ids = new ArrayList<String>();
	                long diff = System.currentTimeMillis() - startTime;
	                System.out.println("Time taken for enriching " + BATCH_SIZE + " words: " + diff/1000 + "s");
	            }
	        }
		}
	}

	@SuppressWarnings("unchecked")
	private List<Node> getNodesList(ArrayList<String> node_ids, String languageId) {
		Map<String, Object> map = new HashMap<String, Object>();
		map.put(LanguageParams.node_ids.name(), node_ids);
		Request getDataNodesRequest = new Request();
		getDataNodesRequest.setRequest(map);
		getDataNodesRequest.setManagerName(GraphEngineManagers.SEARCH_MANAGER);
		getDataNodesRequest.setOperation("getDataNodes");
		getDataNodesRequest.getContext().put(GraphHeaderParams.graph_id.name(), languageId);
		long startTime = System.currentTimeMillis();
		Response response = controllerUtil.getResponse(getDataNodesRequest, LOGGER);
		if(checkError(response)){
			throw new ClientException(LanguageErrorCodes.SYSTEM_ERROR.name(), response.getParams().getErrmsg());
		}
		List<Node> nodeList = (List<Node>) response.get("node_list");
		long diff = System.currentTimeMillis() - startTime;
		System.out.println("Time taken for getting " + BATCH_SIZE + " nodes: " + diff/1000 + "s");
		return nodeList;
	}

	@SuppressWarnings("unchecked")
	private void updateFrequencyCount(String languageId, List<Node> nodes) {
		if (null != nodes && !nodes.isEmpty()) {
			String[] groupBy = new String[] { "pos", "sourceType", "source", "grade" };
			List<String> words = new ArrayList<String>();
			Map<String, Node> nodeMap = new HashMap<String, Node>();
			controllerUtil.getNodeMap(nodes, nodeMap, words);
			if (null != words && !words.isEmpty()) {
				System.out.println("updateFrequencyCount | Total words: " + nodes.size());
				Map<String, Object> indexesMap = new HashMap<String, Object>();
				Map<String, Object> wordInfoMap = new HashMap<String, Object>();
				List<String> groupList = Arrays.asList(groupBy);
				controllerUtil.getIndexInfo(languageId, indexesMap, words, groupList);
				System.out.println("indexesMap size: " + indexesMap.size());
				controllerUtil.getWordInfo(languageId, wordInfoMap, words);
				System.out.println("wordInfoMap size: " + wordInfoMap.size());
				if (null != nodeMap && !nodeMap.isEmpty()) {
					for (Entry<String, Node> entry : nodeMap.entrySet()) {
						Node node = entry.getValue();
						String lemma = entry.getKey();
						boolean update = false;
						Map<String, Object> index = (Map<String, Object>) indexesMap.get(lemma);
						List<Map<String, Object>> wordInfo = (List<Map<String, Object>>) wordInfoMap.get(lemma);
						if (null != index) {
							Map<String, Object> citations = (Map<String, Object>) index.get("citations");
							if (null != citations && !citations.isEmpty()) {
								Object count = citations.get("count");
								if (null != count)
									node.getMetadata().put("occurrenceCount", count);
								controllerUtil.setCountsMetadata(node, citations, "sourceType", null);
								controllerUtil.setCountsMetadata(node, citations, "source", "source");
								controllerUtil.setCountsMetadata(node, citations, "grade", "grade");
								controllerUtil.setCountsMetadata(node, citations, "pos", "pos");
								controllerUtil.addTags(node, citations, "source");
								controllerUtil.updatePosList(node, citations);
								controllerUtil.updateSourceTypesList(node, citations);
								controllerUtil.updateSourcesList(node, citations);
								controllerUtil.updateGradeList(node, citations);
								update = true;
							}
						}
						if (null != wordInfo && !wordInfo.isEmpty()) {
							for (Map<String, Object> info : wordInfo) {
								controllerUtil.updateStringMetadata(node, info, "word", "variants");
								controllerUtil.updateStringMetadata(node, info, "category", "pos_categories");
								controllerUtil.updateStringMetadata(node, info, "gender", "genders");
								controllerUtil.updateStringMetadata(node, info, "number", "plurality");
								controllerUtil.updateStringMetadata(node, info, "pers", "person");
								controllerUtil.updateStringMetadata(node, info, "grammaticalCase", "cases");
								controllerUtil.updateStringMetadata(node, info, "inflection", "inflections");
							}
							update = true;
						}
						if (update) {
							Request updateReq = controllerUtil.getRequest(languageId, GraphEngineManagers.NODE_MANAGER,
									"updateDataNode");
							updateReq.put(GraphDACParams.node.name(), node);
							updateReq.put(GraphDACParams.node_id.name(), node.getIdentifier());
							try {
								Response updateResponse = controllerUtil.getResponse(updateReq, LOGGER);
								if (checkError(updateResponse)) {
								    System.out.println(updateResponse.getParams().getErr());
								    System.out.println(updateResponse.getResult());
									throw new ClientException(LanguageErrorCodes.SYSTEM_ERROR.name(),
											updateResponse.getParams().getErrmsg());
								}
								//System.out.println("update complete for: " + node.getIdentifier());
							} catch (Exception e) {
								System.out.println("Update Frequency Counts error : " + node.getIdentifier() + " : " + e.getMessage());
							}
						}
					}
				}
			}
		}
	}
	
	private void updateSyllablesList(List<Node> nodes){
		 if (null != nodes && !nodes.isEmpty()) {
            System.out.println("updateSyllablesList | Total words: " + nodes.size());
            for (Node node : nodes) {
                try {
                    WordnetUtil.updateSyllables(node);
                    Request updateReq = controllerUtil.getRequest("en", GraphEngineManagers.NODE_MANAGER,
                            "updateDataNode");
                    updateReq.put(GraphDACParams.node.name(), node);
                    updateReq.put(GraphDACParams.node_id.name(), node.getIdentifier());
                    Response updateResponse = controllerUtil.getResponse(updateReq, LOGGER);
                    if (checkError(updateResponse)) {
                        System.out.println(updateResponse.getParams().getErr());
                        System.out.println(updateResponse.getResult());
                        throw new ClientException(LanguageErrorCodes.SYSTEM_ERROR.name(),
                                updateResponse.getParams().getErrmsg());
                    }
                } catch (Exception e) {
                    System.out.println("Update error : " + node.getIdentifier() + " : " + e.getMessage());
                    LOGGER.error("Error updating syllable list for " + node.getIdentifier(), e);
                }
            }
		 }
	}
	
	private void updatePosList(String languageId, List<Node> nodes) {
	    if (null != nodes && !nodes.isEmpty()) {
            System.out.println("updatePosList | Total words: " + nodes.size());
            for (Node node : nodes) {
                try {
                    WordnetUtil.updatePOS(node);
                    Request updateReq = controllerUtil.getRequest(languageId, GraphEngineManagers.NODE_MANAGER,
                            "updateDataNode");
                    updateReq.put(GraphDACParams.node.name(), node);
                    updateReq.put(GraphDACParams.node_id.name(), node.getIdentifier());
                    Response updateResponse = controllerUtil.getResponse(updateReq, LOGGER);
                    if (checkError(updateResponse)) {
                        System.out.println(updateResponse.getParams().getErr());
                        System.out.println(updateResponse.getResult());
                        throw new ClientException(LanguageErrorCodes.SYSTEM_ERROR.name(),
                                updateResponse.getParams().getErrmsg());
                    }
                } catch (Exception e) {
                    System.out.println("Update error : " + node.getIdentifier() + " : " + e.getMessage());
                }
            }
	    }
	}
	
	private void updateWordComplexity(String languageId, List<Node> nodes) {
	    if (null != nodes && !nodes.isEmpty()) {
	        for (Node node : nodes) {
	            try {
                    wordUtil.getWordComplexity(node, languageId);
                } catch (Exception e) {
                    LOGGER.error("Error updating word complexity for " + node.getIdentifier(), e);
                }
	        }
	    }
	}

	@SuppressWarnings("unchecked")
	private void updateLexileMeasures(String languageId, List<Node> nodes) {
		if (null != nodes && !nodes.isEmpty()) {
			System.out.println("updateLexileMeasures | Total words: " + nodes.size());
			List<String> words = new ArrayList<String>();
			Map<String, Node> nodeMap = new HashMap<String, Node>();
			controllerUtil.getNodeMap(nodes, nodeMap, words);
			Request langReq = controllerUtil.getLanguageRequest(languageId,
					LanguageActorNames.LEXILE_MEASURES_ACTOR.name(), LanguageOperations.getWordFeatures.name());
			langReq.put(LanguageParams.words.name(), words);
			Response langRes = controllerUtil.getLanguageResponse(langReq, LOGGER);
			if (checkError(langRes))
				throw new ClientException(LanguageErrorCodes.SYSTEM_ERROR.name(), langRes.getParams().getErrmsg());
			else {
				Map<String, WordComplexity> featureMap = (Map<String, WordComplexity>) langRes
						.get(LanguageParams.word_features.name());
				if (null != featureMap && !featureMap.isEmpty()) {
					System.out.println("Word features returned for " + featureMap.size() + " words");
					for (Entry<String, WordComplexity> entry : featureMap.entrySet()) {
						Node node = nodeMap.get(entry.getKey());
						WordComplexity wc = entry.getValue();
						if (null != node && null != wc) {
							node.getMetadata().put("syllableCount", wc.getCount());
							node.getMetadata().put("syllableNotation", wc.getNotation());
							node.getMetadata().put("unicodeNotation", wc.getUnicode());
							node.getMetadata().put("orthographic_complexity", wc.getOrthoComplexity());
							node.getMetadata().put("phonologic_complexity", wc.getPhonicComplexity());
							try {
							    Request updateReq = controllerUtil.getRequest(languageId, GraphEngineManagers.NODE_MANAGER,
	                                    "updateDataNode");
	                            updateReq.put(GraphDACParams.node.name(), node);
	                            updateReq.put(GraphDACParams.node_id.name(), node.getIdentifier());
								//System.out.println("Sending update req for : " + node.getIdentifier());
								Response updateResponse = controllerUtil.getResponse(updateReq, LOGGER);
								if (checkError(updateResponse)) {
								    System.out.println(updateResponse.getParams().getErr());
                                    System.out.println(updateResponse.getResult());
									throw new ClientException(LanguageErrorCodes.SYSTEM_ERROR.name(),
											updateResponse.getParams().getErrmsg());
								}
								//System.out.println("Update complete for : " + node.getIdentifier());
							} catch (Exception e) {
								System.out.println("Update error : " + node.getIdentifier() + " : " + e.getMessage());
							}
						}
						try {
							updateWordChainRelations(languageId, node, wc);
						} catch (Exception e) {
							System.out.println("Update error : " + node.getIdentifier() + " : " + e.getMessage());
						}
					}
				}
			}
		}
	}
	
	private void updateWordChainRelations(String languageId, Node node, WordComplexity wc) throws Exception {
		if("Live".equalsIgnoreCase((String)node.getMetadata().get(LanguageParams.status.name()))){
			if(languageId.equalsIgnoreCase("en")){
				updateWordChainRelationEnglishLanguage(languageId, node, wc);
			}else{
				updateWordChainRelationIndianLanguage(languageId, node, wc);
			}
		}else{
			List<Relation> outRelation = node.getOutRelations();
			Iterator<Relation> rItr =outRelation.iterator();
			while(rItr.hasNext()){
				Relation rel = rItr.next();
				if(rel.getRelationType().equalsIgnoreCase(RelationTypes.STARTS_WITH_AKSHARA.relationName()) ||
						rel.getRelationType().equalsIgnoreCase(RelationTypes.ENDS_WITH_AKSHARA.relationName()) ||
						rel.getRelationType().equalsIgnoreCase(RelationTypes.RYMING_SOUNDS.relationName())){
					rItr.remove();
				}
			}
			node.setOutRelations(outRelation);
			
			List<Relation> inRelation = node.getInRelations();
			Iterator<Relation> irItr =inRelation.iterator();
			while(irItr.hasNext()){
				Relation rel = irItr.next();
				if(rel.getRelationType().equalsIgnoreCase(RelationTypes.STARTS_WITH_AKSHARA.relationName()) ||
						rel.getRelationType().equalsIgnoreCase(RelationTypes.ENDS_WITH_AKSHARA.relationName()) ||
						rel.getRelationType().equalsIgnoreCase(RelationTypes.RYMING_SOUNDS.relationName())){
					irItr.remove();
				}
			}
			node.setInRelations(inRelation);
			
			Response wordResponse = wordUtil.updateWord(node, languageId, node.getIdentifier());
			if (checkError(wordResponse)) {
				throw new ServerException(LanguageErrorCodes.ERR_UPDATE_WORD.name(),
						getErrorMessage(wordResponse));
			}
		}
	}
	
	private void updateWordChainRelationEnglishLanguage(String languageId, Node node, WordComplexity wc) throws Exception{
		String lemma = (String) node.getMetadata().get(LanguageParams.lemma.name());
		
		//startWithBoundary
		String text = "" + lemma.charAt(0);
		wordUtil.addPhoneticBoundary(languageId, text, node.getIdentifier(), RelationTypes.STARTS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());
		//endstWithBoundary
		text = "" + lemma.charAt(lemma.length()-1);
		wordUtil.addPhoneticBoundary(languageId, text, node.getIdentifier(), RelationTypes.ENDS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());

		//RythmingSound
		String arpabets = wordUtil.getArpabets(lemma);
		if (!StringUtils.isEmpty(arpabets)){
			String arpabetArr[] = arpabets.split("\\s");
			int arpabetLength = arpabetArr.length;
			String rhymingText = (arpabetLength > 3) ? (arpabetArr[arpabetLength-2] + " " + arpabetArr[arpabetLength -1]) : (arpabetArr[arpabetLength -1]); 
			wordUtil.addPhoneticBoundary(languageId, rhymingText, node.getIdentifier(), RelationTypes.RYMING_SOUNDS.relationName(), LanguageParams.RhymingSound.name());
		}

		Node updatedNode = wordUtil.getDataNode(languageId, node.getIdentifier());
		node.setOutRelations(updatedNode.getOutRelations());
		node.setInRelations(updatedNode.getInRelations());
	}
	
	private void updateWordChainRelationIndianLanguage(String languageId, Node node, WordComplexity wc) throws Exception{
		String unicodeNotation = wc.getUnicode().toUpperCase();
		Map<String, String> unicodeTypeMap = wc.getUnicodeTypeMap();
		String syllables[] = StringUtils.split(unicodeNotation);
		
		String firstSyllable = syllables[0];
		String[] firstSyllableUnicodes = parseUnicodes(firstSyllable);
		String firstCharUnicode = firstSyllableUnicodes[0];

		//startWithBoundary
		if(unicodeTypeMap.get(firstCharUnicode).equalsIgnoreCase(SyllableMap.CONSONANT_CODE) || unicodeTypeMap.get(firstCharUnicode).equalsIgnoreCase(SyllableMap.VOWEL_CODE)){
			wordUtil.addPhoneticBoundary(languageId, getTextValue(firstCharUnicode), node.getIdentifier(), RelationTypes.STARTS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());
		}
		
		String lastSyllable = syllables[syllables.length-1];				
		String[] lastSyllableUnicodes = parseUnicodes(lastSyllable);			
		String lastCharUnicode = lastSyllableUnicodes[lastSyllableUnicodes.length-1];
		String secondLastCharUnicode = lastSyllableUnicodes[lastSyllableUnicodes.length-2];
		
		//endstWithBoundary
		if(unicodeTypeMap.get(lastCharUnicode) == null && lastCharUnicode.endsWith("A")){//default vowel
			if(unicodeTypeMap.get(secondLastCharUnicode).equalsIgnoreCase(SyllableMap.CONSONANT_CODE)){
				wordUtil.addPhoneticBoundary(languageId, getTextValue(secondLastCharUnicode), node.getIdentifier(), RelationTypes.ENDS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());
			}
			
		}else if(unicodeTypeMap.get(lastCharUnicode).equalsIgnoreCase(SyllableMap.CONSONANT_CODE)){
			wordUtil.addPhoneticBoundary(languageId, getTextValue(lastCharUnicode), node.getIdentifier(), RelationTypes.ENDS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());
			
		}else if(unicodeTypeMap.get(lastCharUnicode).equalsIgnoreCase(SyllableMap.VOWEL_SIGN_CODE) && unicodeTypeMap.get(secondLastCharUnicode).equalsIgnoreCase(SyllableMap.CONSONANT_CODE)){ 
			//get vowel associated with this vowel_sign
			String vowelUnicode = wordUtil.getVowelUnicode(languageId, lastCharUnicode);
			wordUtil.addPhoneticBoundary(languageId, getTextValue(vowelUnicode), node.getIdentifier(), RelationTypes.ENDS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());
			wordUtil.addPhoneticBoundary(languageId, getTextValue(secondLastCharUnicode), node.getIdentifier(), RelationTypes.ENDS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());
			
		}else if(unicodeTypeMap.get(lastCharUnicode).equalsIgnoreCase(SyllableMap.CLOSE_VOWEL_CODE) && unicodeTypeMap.get(secondLastCharUnicode).equalsIgnoreCase(SyllableMap.CONSONANT_CODE)){
			wordUtil.addPhoneticBoundary(languageId, getTextValue(secondLastCharUnicode), node.getIdentifier(), RelationTypes.ENDS_WITH_AKSHARA.relationName(), LanguageParams.AksharaBoundary.name());
			
		}
		
		//RythmingSound
		if(syllables.length>1){
			String secondLastSyllable = syllables[syllables.length-2];
			String[] secondLastSyllableUnicodes = parseUnicodes(secondLastSyllable);
			String secondLastSyllablelastUnicode = secondLastSyllableUnicodes[secondLastSyllableUnicodes.length-1];
			String rhymingSoundText = "";
			if(!secondLastSyllablelastUnicode.endsWith("A") && unicodeTypeMap.get(secondLastSyllablelastUnicode).equalsIgnoreCase(SyllableMap.VOWEL_SIGN_CODE) || unicodeTypeMap.get(secondLastSyllablelastUnicode).equalsIgnoreCase(SyllableMap.CLOSE_VOWEL_CODE)){
				String secondLastSyllableSecondlastUnicode = secondLastSyllableUnicodes[lastSyllableUnicodes.length-2];
				if(unicodeTypeMap.get(secondLastSyllableSecondlastUnicode).equalsIgnoreCase(SyllableMap.CONSONANT_CODE)){
					rhymingSoundText = "\\"+secondLastSyllableSecondlastUnicode;
				}
			}
			rhymingSoundText += lastSyllable;
			wordUtil.addPhoneticBoundary(languageId, rhymingSoundText, node.getIdentifier(), RelationTypes.RYMING_SOUNDS.relationName(), LanguageParams.RhymingSound.name());
			
		}
		
		Node updatedNode = wordUtil.getDataNode(languageId, node.getIdentifier());
		node.setOutRelations(updatedNode.getOutRelations());
		node.setInRelations(updatedNode.getInRelations());
	}
	
	private String[] parseUnicodes(String syllable){
		
		String[] syllableUnicodes = syllable.split("\\\\");
		List<String> list = new ArrayList<String>();

		//trim modifier unicode
		for(String s: syllableUnicodes){
			if(StringUtils.isNotEmpty(s)){
				if(s.endsWith("M"))
					s=s.substring(0, 4);
				list.add(s);
			}
		}
		return list.toArray(new String[list.size()]);
	}
	
	private String getTextValue(String unicode){
		int hexVal = Integer.parseInt(unicode, 16);
		return ""+(char)hexVal;
	}
	
	@Override
	protected void invokeMethod(Request request, ActorRef parent) {
	}
}