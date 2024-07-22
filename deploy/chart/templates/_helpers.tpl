{{/*
Expand the name of the chart.
*/}}
{{- define "chart-test.name" -}}
{{- default .Chart.Name .Values.vcsFacade.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "chart-test.fullname" -}}
{{- if .Values.vcsFacade.fullnameOverride }}
{{- .Values.vcsFacade.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.vcsFacade.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "chart-test.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "chart-test.labels" -}}
helm.sh/chart: {{ include "chart-test.chart" . }}
{{ include "chart-test.selectorLabels" . }}
{{- if .Values.vcsFacade.image.version }}
app/version: {{ .Values.vcsFacade.image.version | quote }}
{{- end }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end }}

{{/*
Selector labels
*/}}
{{- define "chart-test.selectorLabels" -}}
app.kubernetes.io/name: {{ include "chart-test.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "bitbucket-db.selectorLabels" -}}
app.kubernetes.io/name: {{ include "bitbucketDb.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{/*
Create the name of the service account to use
*/}}
{{- define "chart-test.serviceAccountName" -}}
{{- if .Values.vcsFacade.serviceAccount.create }}
{{- default (include "chart-test.fullname" .) .Values.vcsFacade.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.vcsFacade.serviceAccount.name }}
{{- end }}
{{- end }}

{{- define "gitea.fullname" -}}
{{- if .Values.gitea.fullnameOverride }}
{{- printf "%s-%s" .Release.Name .Values.gitea.fullnameOverride }}
{{- end }}
{{- end }}

{{- define "bitbucket.fullname" -}}
{{- if .Values.bitbucket.fullnameOverride }}
{{- printf "%s-%s" .Release.Name .Values.bitbucket.fullnameOverride }}
{{- end }}
{{- end }}

{{- define "bitbucketDb.fullname" -}}
{{- if .Values.bitbucketDb.fullnameOverride }}
{{- printf "%s-%s" .Release.Name .Values.bitbucketDb.fullnameOverride }}
{{- end }}
{{- end }}

{{- define "vcsFacade.fullname" -}}
{{- if .Values.vcsFacade.fullnameOverride }}
{{- printf "%s-%s" .Release.Name .Values.vcsFacade.fullnameOverride }}
{{- end }}
{{- end }}

{{- define "opensearch.fullname" -}}
{{- if .Values.opensearch.fullnameOverride }}
{{- printf "%s-%s" .Release.Name .Values.opensearch.fullnameOverride }}
{{- end }}
{{- end }}
