(ns cayenne.data.publisher
  (:require [cayenne.conf :as conf]
            [cayenne.api.v1.response :as r]
            [cayenne.api.v1.query :as query]
            [cayenne.api.v1.filter :as filter]
            [cayenne.formats.citeproc :as citeproc]
            [cayenne.ids.prefix :as prefix]
            [clojure.string :as string]
            [somnium.congomongo :as m]))

(def solr-publisher-id-field "owner_prefix")

(defn get-solr-works [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-publisher-id-field
                                  :filters filter/std-filters))
      (.getResults)))

(defn get-solr-work-count [query-context]
  (-> (conf/get-service :solr)
      (.query (query/->solr-query query-context 
                                  :id-field solr-publisher-id-field
                                  :paged false
                                  :count-only true))
      (.getResults)
      (.getNumFound)))

(defn fetch-one [query-context]
  (let [pub-doc (m/with-mongo (conf/get-service :mongo)
                  (m/fetch-one "publishers"
                               :where {:id (-> query-context
                                               (:id))}))]
    (when pub-doc
      (r/api-response :publisher
                      :content
                      {:id (:id query-context)
                       :name (:name pub-doc)
                       :tokens (:tokens pub-doc)
                       :work-count (get-solr-work-count query-context)}))))

(defn fetch [query-context]
  ())

(defn fetch-works [query-context]
  (let [doc-list (get-solr-works query-context)]
    (-> (r/api-response :work-list)
        (r/with-query-context-info query-context)
        (r/with-result-items 
          (.getNumFound doc-list)
          (map citeproc/->citeproc doc-list)))))

;; faked publisher name look up we will use for the moment until
;; we have proper publisher ids
(defn fetch-one [query-context]
  (let [any-work (-> (assoc query-context :rows (int 1))
                     (get-solr-works)
                     (first))
        citeproc-doc (if-not (nil? any-work) (citeproc/->citeproc any-work) {})]
    (if (and 
         (not (nil? any-work))
         (not (string/blank? (:publisher citeproc-doc))))
      (r/api-response :publisher :content {:prefix (:id query-context)
                                           :name (:publisher citeproc-doc)})
      (r/api-response :publisher :content {:prefix (:id query-context)}))))

