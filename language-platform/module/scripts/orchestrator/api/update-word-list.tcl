package require java
java::import -package java.util ArrayList List
java::import -package java.util HashMap Map
java::import -package org.ekstep.graph.dac.model Node

set lemma_list [java::new ArrayList]
set object_type "Word"
set collection_type "SET"
set set_type "WordList"
set wordIds [java::new ArrayList]

set isWordNull [java::isnull $words]
if {$isWordNull == 0} {
	set map [java::new HashMap]
	$map put "nodeType" "DATA_NODE"
	$map put "objectType" $object_type
	$map put "lemma" $words
	set search_criteria [create_search_criteria $map]
	set search_response [searchNodes $language_id $search_criteria]
	set check_error [check_response_error $search_response]
	if {$check_error} {
		return $search_response;
	} else {
		set graph_nodes [get_resp_value $search_response "node_list"]
		set word_id_list [java::new ArrayList]
		java::for {Node graph_node} $graph_nodes {
			set word_id [java::prop $graph_node "identifier"]
			$word_id_list add $word_id
		}
		$wordIds addAll $word_id_list
		set resp [addMembers $language_id $wordlist_id $collection_type $word_id_list]
		set check_error_add_member [check_response_error $resp]
		if {$check_error_add_member} {
			return $resp
		}
	}
}

set isRemoveWordsNull [java::isnull $removeWords]
if {$isRemoveWordsNull == 0} {
	set rm_map [java::new HashMap]
	$rm_map put "nodeType" "DATA_NODE"
	$rm_map put "objectType" $object_type
	$rm_map put "lemma" $removeWords
	set rm_search_criteria [create_search_criteria $rm_map]
	set rm_search_response [searchNodes $language_id $rm_search_criteria]
	set rm_check_error [check_response_error $rm_search_response]
	if {$rm_check_error} {
		return $rm_search_response;
	} else {
		set rm_graph_nodes [get_resp_value $rm_search_response "node_list"]
		set rm_word_id_list [java::new ArrayList]
		java::for {Node rm_graph_node} $rm_graph_nodes {
			set rm_word_id [java::prop $rm_graph_node "identifier"]
			$rm_word_id_list add $rm_word_id
		}
		$wordIds addAll $rm_word_id_list
		set rm_resp [removeMembers $language_id $wordlist_id $collection_type $rm_word_id_list]
		set check_error_rm_member [check_response_error $rm_resp]
		if {$check_error_rm_member} {
			return $rm_resp
		}
	}
}
updateWordListMembership $language_id $wordIds

set get_resp [getWordList $language_id $wordlist_id]
set get_resp_check_error [check_response_error $get_resp]
if {$get_resp_check_error} {
	return $get_resp;
}
set word_list_node [get_resp_value $get_resp "wordlist"]
set words_list_obj [$word_list_node get "words"]
set words_list [java::cast ArrayList $words_list_obj]
set words_list_size [$words_list size]

if { $words_list_size > 0 } {
	set up_map [java::new HashMap]
	$up_map put "nodeType" "DATA_NODE"
	$up_map put "objectType" $object_type
	$up_map put "lemma" $words_list
	set up_search_criteria [create_search_criteria $up_map]
	set up_search_response [searchNodes $language_id $up_search_criteria]
	set up_check_error [check_response_error $up_search_response]
	if {$up_check_error} {
		return $up_search_response;
	} else {
		set up_graph_nodes [get_resp_value $up_search_response "node_list"]
		set up_word_id_list [java::new ArrayList]
		java::for {Node up_graph_node} $up_graph_nodes {
			set up_word_id [java::prop $up_graph_node "identifier"]
			$up_word_id_list add $up_word_id
		}
		set set_node [java::new Node]
		java::prop $set_node "identifier" $wordlist_id
		java::prop $set_node "metadata" $metadata
		set up_resp [updateSet $language_id $up_word_id_list $set_type $object_type $set_node]
		set check_error_up_resp [check_response_error $up_resp]
		return $up_resp
	}
} else {
	return "Ok"
}