FROM mcr.microsoft.com/presidio-analyzer:latest

USER root
COPY conf/nlp.yaml /app/presidio_analyzer/conf/pii-nlp.yaml
COPY conf/recognizers.yaml /app/presidio_analyzer/conf/pii-recognizers.yaml
COPY conf/analyzer.yaml /app/presidio_analyzer/conf/pii-analyzer.yaml
RUN python install_nlp_models.py --conf_file presidio_analyzer/conf/pii-nlp.yaml
USER 1001

ENV NLP_CONF_FILE=presidio_analyzer/conf/pii-nlp.yaml \
    RECOGNIZER_REGISTRY_CONF_FILE=presidio_analyzer/conf/pii-recognizers.yaml \
    ANALYZER_CONF_FILE=presidio_analyzer/conf/pii-analyzer.yaml
