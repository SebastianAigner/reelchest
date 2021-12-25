console.log("Hello, world!")
let elems = document.getElementsByClassName("dlmgr-progress")
var arr = Array.from(elems)

function updateProgress() {


    arr.forEach((el) => {
        fetch("/progress/" + el.id).then(response =>
            response.text()
        ).then(text =>
            el.innerText = (parseFloat(text) * 100).toFixed(2)
        )
    })

}

setInterval(updateProgress, 500);