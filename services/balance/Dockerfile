FROM golang:1.22-alpine AS build
WORKDIR /src
COPY go.mod .
COPY main.go .
RUN go mod download && go mod tidy && go build -o app

FROM alpine:3.20
WORKDIR /app
ENV SERVICE_NAME=balance
COPY --from=build /src/app /app/app
CMD ["/app/app"]

