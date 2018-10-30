//TODO: to be integrated into main.js once it works

let fs = require('fs');
let es = require('event-stream');
let lzma = require('lzma-native');

const elasticsearch = require('elasticsearch');

//Create client
let client = new elasticsearch.Client({
    // hosts: ['localhost:9200', 'localhost:9201'],
    host: 'localhost:9200',
    log: 'error',
    keepAlive: true
    // sniffOnConnectionFault: true,
    // sniffOnStart: true,
    // sniffInterval: 300,
    // suggestCompression: true
});

// let analyserPath = "./resources/analyzer.json";
let analyserPath = "/Users/lfoppiano/development/scienceminer/biblio-glutton/matching/resources/analyzer.json";
let mappingPath = "./resources/crossref_mapping.json";

let input = '/Volumes/SEAGATE1TB/scienceminer/crossref/crossref-works.2018-09-05.json.xz';

function createIndex(index) {
    var analyserFile = fs.readFileSync(analyserPath, 'utf-8');
    var mappingFile = fs.readFileSync(mappingPath, 'utf-8').toString();

    client.indices
        .create(
            {
                index: index,
                ignore: [404, 400],
                mapping: mappingFile
            },
            function (err, resp) {
                if (err)
                    console.log('Failed to create ElasticSearch index, ' + err.message);
                else {
                    console.log('Successfully created ElasticSearch index');
                    // client.indexes.putMapping(mappingFile)
                }
            }
        );
}

function delete_index(index) {
    client.indices
        .delete(
            {
                index: index,
                ignore: [404]
            },
            function (error, response) {
                if (error) {
                    console.log("Error deleting index: " + error)
                }
            }
        );
}

async function init_cluster(index) {
    try {
        await client.ping({
            requestTimeout: 30000,
        }).then(function (body) {
            console.log("Cluster alive");
        }, function (error) {
            console.log("Cannot contact cluster: " + error);
            process.exit(-1);
        });

        await delete_index(index);
        await createIndex(index);
        console.log('Init done.')
    } catch (err) {
        console.log(err)
    }
}

init_cluster("crossref")
    .then(body => {
            console.log("Loading file " + input);
            load_file(input);
        }
    );


function load_file(input) {
    fs.createReadStream(input)
        .pipe(lzma.createDecompressor())
        .pipe(es.split())
        .pipe(es.map(function (data, cb) {
            // prepare/massage the data

            // - migrate id from '_id' to 'id'
            var obj = JSON.parse(data);
            obj.id = obj._id.$oid;
            delete obj._id;

            // - remove citation data
            delete obj.reference;

            cb(null, obj)
        }))
        .pipe(es.map(function (line, cb) {

            var response = undefined;
            try {

                response = client.index({
                    index: 'crossref',
                    type: 'item',
                    body: line
                });

            } catch (error) {
                console.trace(error)
            }
            cb(null, response)
        }))
        .on('error',
            function (error) {
                console.log("Error occurred: " + error);
            }
        )
        .on('finish',
            function () {
                console.log("Finished. ")
            }
        );
}


