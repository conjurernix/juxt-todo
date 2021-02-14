(ns pro.juxt.http.middleware
  (:require [pro.juxt.http.auth :refer [wrap-authorization]]
            [muuntaja.middleware :as middleware]
            [ring.util.http-response :refer [not-found bad-request unauthorized internal-server-error]]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.tools.logging :as log])
  (:import (clojure.lang ExceptionInfo)))

(defn wrap-errors [handler]
  (fn [req]
    (try
      (handler req)

      (catch ExceptionInfo e
        (log/error e)
        (let [exception-data (ex-data e)]
          (case (:pro.juxt/error-type exception-data)
            :pro.juxt/not-found (not-found exception-data)
            :pro.juxt/validation-error (bad-request exception-data)
            :pro.juxt/unauthorized (unauthorized "Unauthorized")
            (internal-server-error "Oops, something went wrong"))))

      (catch Exception e
        (log/error e)
        (internal-server-error "Oops, something went wrong")))))

(defn remove-ns-from-res [x]
  (let [remove-ns-from-map (partial clojure.walk/walk (fn [[k v]] [(name k) v])
                                    identity)]
    (cond (map? x) (remove-ns-from-map x)
          (seq? x) (map remove-ns-from-map x)
          :else x)))

(defn wrap-remove-ns [handler]
  (fn [req]
    (let [res (handler req)]
      (update res :body remove-ns-from-res))))

(defn wrap-middleware [handler]
  (-> handler
      wrap-authorization
      wrap-errors
      wrap-params
      wrap-remove-ns
      middleware/wrap-format))
