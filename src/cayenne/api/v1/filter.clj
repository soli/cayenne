(ns cayenne.api.v1.filter
  (:require [clojure.string :as string]
            [cayenne.ids.fundref :as fundref]
            [cayenne.ids.prefix :as prefix]
            [cayenne.ids.orcid :as orcid]))

; build solr filters

(defn field-is [field-name match]
  (str field-name ":" match))

(defn field-gt [field-name val]
  (str field-name ":[" (+ 1 val) " TO *]"))

(defn field-gte [field-name val]
  (str field-name ":[" val " TO *]"))

(defn field-lt [field-name val]
  (str field-name ":[* TO " (- val 1) "]"))

(defn field-lte [field-name val]
  (str field-name ":[* TO " val "]"))

(defn q-or [& more]
  (str "(" (string/join " " (interpose "OR" more)) ")"))

(defn q-and [& more]
  (str "(" (string/join " " (interpose "AND" more)) ")"))

(defn field-lt-or-gt [field-name val end-point]
  (cond 
   (= end-point :from)
   (field-gt field-name val)
   (= end-point :until)
   (field-lt field-name val)))

(defn field-lte-or-gte [field-name val end-point]
  (cond
   (= end-point :from)
   (field-gte field-name val)
   (= end-point :until)
   (field-lte field-name val)))

(defn split-date [date-str]
  (let [date-parts (string/split date-str #"-")]
    {:year (Integer/parseInt (first date-parts))
     :month (Integer/parseInt (nth date-parts 1 "-1"))
     :day (Integer/parseInt (nth date-parts 2 "-1"))}))

(defn stamp-date [date-stamp-field direction]
  (fn [val]
    ()))

(defn particle-date [year-field month-field day-field end-point]
  (fn [val]
    (let [d (split-date val)]
      (cond (not= (:day d) -1)
            (q-or
             (field-lt-or-gt year-field (:year d) end-point)
             (q-and (field-is year-field (:year d))
                    (field-lt-or-gt month-field (:month d) end-point))
             (q-and (field-is year-field (:year d))
                    (field-is month-field (:month d))
                    (field-lte-or-gte day-field (:day d) end-point)))
            (not= (:month d) -1)
            (q-or
             (field-lt-or-gt year-field (:year d) end-point)
             (q-and (field-is year-field (:year d))
                    (field-lte-or-gte month-field (:month d) end-point)))
            (:year d)
             (field-lte-or-gte year-field (:year d) end-point)))))

(defn existence [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          (str field ":[* TO *]")
          (#{"f" "false" "0"} (.toLowerCase val))
          (str "-" field ":[* TO *]"))))

(defn bool [field]
  (fn [val]
    (cond (#{"t" "true" "1"} (.toLowerCase val))
          (str field ":true")
          (#{"f" "false" "0"} (.toLowerCase val))
          (str field ":false"))))

(defn equality [field & {:keys [transformer] :or {transformer identity}}]
  (fn [val] (str field ":\"" (transformer val) "\"")))

(defn compound [ordering]
  (fn [m]
    (->> ordering
         (filter m)
         (map #(str % ":\"" (get m %) "\"")) ; change
         (q-and))))

;; pass vars to compounds - map of sub key and vals
;; fix above
;; populate dynamic fields in solr.clj
;; reindex

(def std-filters
  {"from-update-date" (stamp-date "deposited_at" :from)
   "until-update-date" (stamp-date "deposited_at" :until)
   "from-pub-date" (particle-date "year" "month" "day" :from)
   "until-pub-date" (particle-date "year" "month" "day" :until)
   "has-full-text" (existence "full_text_url") ;in new index
   "has-license" (existence "license_url") ;in new index
   "has-references" (bool "references") ;in new index
   "has-archive" (existence "archive") ;waiting for schema change
   "has-orcid" (existence "orcid")
;   "full-text" (compound "full_text" ["type" "version"])
;   "license" (compound "license" ["url" "version" "delay"])
   "orcid" (equality "orcid" :transformer orcid/to-orcid-uri)
   "publisher" (equality "owner_prefix" :transformer prefix/to-prefix-uri) ;in new index
   "funder" (equality "funder_doi" :transformer fundref/id-to-doi-uri)})