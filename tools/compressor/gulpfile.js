const fs = require('fs');
const path = require('path');
const gulp = require('gulp');
const sharp = require('sharp');
const tmp = require('tmp');
const execSync = require('child_process').execSync;
const readdirp = require('readdirp');

const assetsPath = '../../app/src/uncompressed_assets';
const assetFilter = ['*.jpg', '*.jpeg', '*.png'];

function compressAsset(source, target, srgb, alpha, done) {
    if (fs.existsSync(target)) {
        console.log("Already compressed: " + target);
        done();
        return;
    }

    const extension = path.extname(source);
    if (extension !== ".png") {
        // Etc2Tool requires png format
        const tempImage = tmp.tmpNameSync() + ".png";
        sharp(source)
        .toFile(tempImage)
        .then(data => {
            compressAsset(tempImage, target, srgb, false, done);
        })
        .catch(err => {
            console.log("PNG conversion failed for " + source + ": " + err);
            done(err);
        });
        return;
    }

    try {
        console.log("About to compress: " + target);
        var format;
        if (srgb) {
            format = alpha  ? "SRGBA8" : "SRGB8"
        } else {
            format = alpha  ? "RGBA8" : "RGB8"
        }
        var out = execSync(`EtcTool ${source} -output ${target} -format ${format} -effort 100 -v`).toString();
        console.log(out);
        done();
    } catch (err) {
        console.error(err.message);
        done(err);
    }
}

function compress(done) {
    let pending = 0;
    let finished = false;
    const settings = {
        fileFilter: assetFilter
    };
    readdirp(assetsPath, settings)
    .on('data', function (entry) {
        pending++;
        const name = entry.fullPath;
        const extension = path.extname(name);
        let target = name.slice(0, -extension.length) + ".ktx";
        target = target.replace("uncompressed_assets/", "");

        if (target.indexOf("cubemap" >= 0)) {
            // Generate SRGBA versions for cubemaps
            pending++;
            const srgb = true;
            const alpha = false;
            compressAsset(name, target.replace(".ktx", "_srgb.ktx"), srgb, alpha, function() {
                pending--;
                if (finished && !pending) {
                    done();
                }
            });
        }
        const srgb = false;
        const alpha = true;
        compressAsset(name, target, srgb, true, function() {
            pending--;
            if (finished && !pending) {
                done();
            }
        });
    })
    .on('warn', function(warn){
        console.log("Warn: ", warn);
    })
    .on('error', function(err){
        console.log("Error: ", err);
    })
    .on('end', function(){
        finished = true;
        if (!pending) {
            done();
        }
    });
}

exports.compress = compress;
